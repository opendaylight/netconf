/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import com.google.common.base.Preconditions;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
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
        this.strategy = Preconditions.checkNotNull(strategy);
        this.address = Preconditions.checkNotNull(address);
        this.bootstrap = Preconditions.checkNotNull(bootstrap);
    }

    @SuppressWarnings("checkstyle:illegalCatch")
    synchronized void connect() {
        try {
            final int timeout = this.strategy.getConnectTimeout();

            LOG.debug("Promise {} attempting connect for {}ms", this, timeout);

            if (this.address.isUnresolved()) {
                this.address = new InetSocketAddress(this.address.getHostName(), this.address.getPort());
            }
            this.bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout);
            final ChannelFuture connectFuture = this.bootstrap.connect(this.address);
            // Add listener that attempts reconnect by invoking this method again.
            connectFuture.addListener(new BootstrapConnectListener());
            this.pending = connectFuture;
        } catch (final Exception e) {
            LOG.info("Failed to connect to {}", address, e);
            setFailure(e);
        }
    }

    @Override
    public synchronized boolean cancel(final boolean mayInterruptIfRunning) {
        if (super.cancel(mayInterruptIfRunning)) {
            this.pending.cancel(mayInterruptIfRunning);
            return true;
        }

        return false;
    }

    @Override
    public synchronized Promise<S> setSuccess(final S result) {
        LOG.debug("Promise {} completed", this);
        this.strategy.reconnectSuccessful();
        return super.setSuccess(result);
    }

    private class BootstrapConnectListener implements ChannelFutureListener {
        @Override
        public void operationComplete(final ChannelFuture cf) {
            synchronized (NetconfSessionPromise.this) {

                LOG.debug("Promise {} connection resolved", NetconfSessionPromise.this);

                // Triggered when a connection attempt is resolved.
                Preconditions.checkState(NetconfSessionPromise.this.pending.equals(cf));

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
                        LOG.debug("Closing channel for cancelled promise {}", NetconfSessionPromise.this);
                        cf.channel().close();
                    }
                    return;
                }

                if (cf.isSuccess()) {
                    LOG.debug("Promise {} connection successful", NetconfSessionPromise.this);
                    return;
                }

                LOG.debug("Attempt to connect to {} failed", NetconfSessionPromise.this.address, cf.cause());

                final Future<Void> rf = NetconfSessionPromise.this.strategy.scheduleReconnect(cf.cause());
                rf.addListener(new ReconnectingStrategyListener());
                NetconfSessionPromise.this.pending = rf;
            }
        }

        private class ReconnectingStrategyListener implements FutureListener<Void> {
            @Override
            public void operationComplete(final Future<Void> sf) {
                synchronized (NetconfSessionPromise.this) {
                    // Triggered when a connection attempt is to be made.
                    Preconditions.checkState(NetconfSessionPromise.this.pending.equals(sf));

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
        }
    }
}
