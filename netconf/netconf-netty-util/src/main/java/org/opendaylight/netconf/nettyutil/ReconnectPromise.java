/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static java.util.Objects.requireNonNull;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.opendaylight.netconf.api.NetconfSession;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.nettyutil.AbstractNetconfDispatcher.PipelineInitializer;
import org.opendaylight.yangtools.yang.common.Empty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
final class ReconnectPromise<S extends NetconfSession, L extends NetconfSessionListener<? super S>>
        extends DefaultPromise<Empty> implements ReconnectFuture {
    private static final Logger LOG = LoggerFactory.getLogger(ReconnectPromise.class);

    private final AbstractNetconfDispatcher<S, L> dispatcher;
    private final InetSocketAddress address;
    private final ReconnectStrategyFactory strategyFactory;
    private final Bootstrap bootstrap;
    private final PipelineInitializer<S> initializer;
    private final Promise<Empty> firstSessionFuture;
    /**
     * Channel handler that responds to channelInactive event and reconnects the session unless the promise is
     * cancelled.
     */
    private final ChannelInboundHandlerAdapter inboundHandler = new ChannelInboundHandlerAdapter() {
        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            // This is the ultimate channel inactive handler, not forwarding
            if (isCancelled()) {
                return;
            }

            synchronized (ReconnectPromise.this) {
                final Future<?> attempt = pending;
                if (!attempt.isDone() || !attempt.isSuccess()) {
                    // Connection refused, negotiation failed, or similar
                    LOG.debug("Connection to {} was dropped during negotiation, reattempting", address);
                }

                LOG.debug("Reconnecting after connection to {} was dropped", address);
                lockedConnect();
            }
        }
    };

    @GuardedBy("this")
    private Future<?> pending;

    ReconnectPromise(final EventExecutor executor, final AbstractNetconfDispatcher<S, L> dispatcher,
            final InetSocketAddress address, final ReconnectStrategyFactory connectStrategyFactory,
            final Bootstrap bootstrap, final PipelineInitializer<S> initializer) {
        super(executor);
        this.firstSessionFuture = new DefaultPromise<>(executor);
        this.bootstrap = requireNonNull(bootstrap);
        this.initializer = requireNonNull(initializer);
        this.dispatcher = requireNonNull(dispatcher);
        this.address = requireNonNull(address);
        this.strategyFactory = requireNonNull(connectStrategyFactory);
    }

    @Override
    public synchronized boolean cancel(final boolean mayInterruptIfRunning) {
        if (super.cancel(mayInterruptIfRunning)) {
            firstSessionFuture.cancel(mayInterruptIfRunning);
            pending.cancel(mayInterruptIfRunning);
            return true;
        }
        return false;
    }

    @Override
    public Future<?> firstSessionFuture() {
        return firstSessionFuture;
    }

    synchronized void connect() {
        lockedConnect();
    }

    @Holding("this")
    private void lockedConnect() {
        final ReconnectStrategy cs = strategyFactory.createReconnectStrategy();

        // Set up a client with pre-configured bootstrap, but add a closed channel handler into the pipeline to support
        // reconnect attempts
        pending = dispatcher.createClient(address, cs, bootstrap, (channel, promise) -> {
            initializer.initializeChannel(channel, promise);
            // add closed channel handler
            // This handler has to be added as last channel handler and the channel inactive event has to be caught by
            // it
            // Handlers in front of it can react to channelInactive event, but have to forward the event or the
            // reconnect will not work
            // This handler is last so all handlers in front of it can handle channel inactive (to e.g. resource
            // cleanup) before a new connection is started
            channel.pipeline().addLast(inboundHandler);
        });

        if (!firstSessionFuture.isDone()) {
            pending.addListener(future -> {
                if (!future.isSuccess() && !firstSessionFuture.isDone()) {
                    firstSessionFuture.setFailure(future.cause());
                }
            });
        }
    }
}
