/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.opendaylight.netconf.api.NetconfSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
final class NetconfSessionPromise<S extends NetconfSession> extends DefaultPromise<S> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfSessionPromise.class);
    private final ReconnectStrategy strategy;
    private InetSocketAddress address;
    private final Bootstrap bootstrap;

    @GuardedBy("this")
    private Future<?> pending;

    NetconfSessionPromise(final EventExecutor executor, final InetSocketAddress address,
            final ReconnectStrategy strategy, final Bootstrap bootstrap) {
        super(executor);
        this.strategy = requireNonNull(strategy);
        this.address = requireNonNull(address);
        this.bootstrap = requireNonNull(bootstrap);
    }

    @SuppressWarnings("checkstyle:illegalCatch")
    synchronized void connect() {
        try {
            final int timeout = strategy.getConnectTimeout();

            LOG.debug("Promise {} attempting connect for {}ms", this, timeout);

            if (address.isUnresolved()) {
                address = new InetSocketAddress(address.getHostName(), address.getPort());
            }
            final ChannelFuture connectFuture = bootstrap.connect(address);
            // Add listener that attempts reconnect by invoking this method again.
            connectFuture.addListener((ChannelFutureListener) this::channelConnectComplete);
            pending = connectFuture;
        } catch (final Exception e) {
            LOG.info("Failed to connect to {}", address, e);
            setFailure(e);
        }
    }

    @Override
    public synchronized boolean cancel(final boolean mayInterruptIfRunning) {
        if (super.cancel(mayInterruptIfRunning)) {
            pending.cancel(mayInterruptIfRunning);
            return true;
        }

        return false;
    }

    @Override
    public synchronized Promise<S> setSuccess(final S result) {
        LOG.debug("Promise {} completed", this);
        strategy.reconnectSuccessful();
        return super.setSuccess(result);
    }

    // Triggered when a connection attempt is resolved.
    private synchronized void channelConnectComplete(final ChannelFuture cf) {
        LOG.debug("Promise {} connection resolved", this);
        checkState(pending.equals(cf));

        /*
         * The promise we gave out could have been cancelled,
         * which cascades to the connect getting cancelled,
         * but there is a slight race window, where the connect
         * is already resolved, but the listener has not yet
         * been notified -- cancellation at that point won't
         * stop the notification arriving, so we have to close
         * the race here.
         */
        if (isCancelled()) {
            if (cf.isSuccess()) {
                LOG.debug("Closing channel for cancelled promise {}", this);
                cf.channel().close();
            }
            return;
        }

        if (cf.isSuccess()) {
            LOG.debug("Promise {} connection successful", this);
            return;
        }

        LOG.debug("Attempt to connect to {} failed", address, cf.cause());

        final Future<Void> rf = strategy.scheduleReconnect(cf.cause());
        pending = rf;
        rf.addListener(this::reconnectFutureComplete);
    }

    // Triggered when a connection attempt is to be made.
    private synchronized void reconnectFutureComplete(final Future<?> sf) {
        LOG.debug("Promise {} strategy triggered reconnect", this);
        verify(pending == sf, "Completed strategy future %s while pending %s", sf, pending);

        /*
         * The promise we gave out could have been cancelled,
         * which cascades to the reconnect attempt getting
         * cancelled, but there is a slight race window, where
         * the reconnect attempt is already enqueued, but the
         * listener has not yet been notified -- if cancellation
         * happens at that point, we need to catch it here.
         */
        if (!isCancelled()) {
            if (sf.isSuccess()) {
                connect();
            } else {
                setFailure(sf.cause());
            }
        }
    }
}
