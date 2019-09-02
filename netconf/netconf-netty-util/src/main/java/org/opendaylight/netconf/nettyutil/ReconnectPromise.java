/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import org.opendaylight.netconf.api.NetconfSession;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
final class ReconnectPromise<S extends NetconfSession, L extends NetconfSessionListener<? super S>>
        extends DefaultPromise<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(ReconnectPromise.class);

    private final AbstractNetconfDispatcher<S, L> dispatcher;
    private final InetSocketAddress address;
    private final ReconnectStrategyFactory strategyFactory;
    private final Bootstrap bootstrap;
    private final AbstractNetconfDispatcher.PipelineInitializer<S> initializer;
    private Future<?> pending;

    ReconnectPromise(final EventExecutor executor, final AbstractNetconfDispatcher<S, L> dispatcher,
            final InetSocketAddress address, final ReconnectStrategyFactory connectStrategyFactory,
            final Bootstrap bootstrap, final AbstractNetconfDispatcher.PipelineInitializer<S> initializer) {
        super(executor);
        this.bootstrap = bootstrap;
        this.initializer = requireNonNull(initializer);
        this.dispatcher = requireNonNull(dispatcher);
        this.address = requireNonNull(address);
        this.strategyFactory = requireNonNull(connectStrategyFactory);
    }

    synchronized void connect() {
        final ReconnectStrategy cs = this.strategyFactory.createReconnectStrategy();

        // Set up a client with pre-configured bootstrap, but add a closed channel handler into the pipeline to support
        // reconnect attempts
        pending = this.dispatcher.createClient(this.address, cs, bootstrap, (channel, promise) -> {
            initializer.initializeChannel(channel, promise);
            // add closed channel handler
            // This handler has to be added as last channel handler and the channel inactive event has to be caught by
            // it
            // Handlers in front of it can react to channelInactive event, but have to forward the event or the
            // reconnect will not work
            // This handler is last so all handlers in front of it can handle channel inactive (to e.g. resource
            // cleanup) before a new connection is started
            channel.pipeline().addLast(new ClosedChannelHandler(ReconnectPromise.this));
        });

        pending.addListener(future -> {
            if (!future.isSuccess() && !ReconnectPromise.this.isDone()) {
                ReconnectPromise.this.setFailure(future.cause());
            }
        });
    }

    /**
     * Indicate if the initial connection succeeded.
     *
     * @return true if initial connection was established successfully, false if initial connection failed due to e.g.
     *         Connection refused, Negotiation failed
     */
    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private synchronized boolean isInitialConnectFinished() {
        requireNonNull(pending);
        return pending.isDone() && pending.isSuccess();
    }

    @Override
    public synchronized boolean cancel(final boolean mayInterruptIfRunning) {
        if (super.cancel(mayInterruptIfRunning)) {
            requireNonNull(pending);
            this.pending.cancel(mayInterruptIfRunning);
            return true;
        }

        return false;
    }

    /**
     * Channel handler that responds to channelInactive event and reconnects the session.
     * Only if the promise was not canceled.
     */
    private static final class ClosedChannelHandler extends ChannelInboundHandlerAdapter {
        private final ReconnectPromise<?, ?> promise;

        ClosedChannelHandler(final ReconnectPromise<?, ?> promise) {
            this.promise = promise;
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            // This is the ultimate channel inactive handler, not forwarding
            if (promise.isCancelled()) {
                return;
            }

            if (promise.isInitialConnectFinished() == false) {
                LOG.debug("Connection to {} was dropped during negotiation, reattempting", promise.address);
            }

            LOG.debug("Reconnecting after connection to {} was dropped", promise.address);
            promise.connect();
        }
    }
}
