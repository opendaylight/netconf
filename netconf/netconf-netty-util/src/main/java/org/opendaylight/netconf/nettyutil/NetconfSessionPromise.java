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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.opendaylight.netconf.api.NetconfSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
final class NetconfSessionPromise<S extends NetconfSession> extends DefaultPromise<S> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfSessionPromise.class);

    private final Bootstrap bootstrap;
    private InetSocketAddress address;

    @GuardedBy("this")
    private ChannelFuture pending;

    NetconfSessionPromise(final EventExecutor executor, final InetSocketAddress address, final Bootstrap bootstrap) {
        super(executor);
        this.address = requireNonNull(address);
        this.bootstrap = requireNonNull(bootstrap);
    }

    @SuppressWarnings("checkstyle:illegalCatch")
    synchronized void connect() {
        final ChannelFuture connectFuture;
        try {
            if (address.isUnresolved()) {
                address = new InetSocketAddress(address.getHostName(), address.getPort());
            }
            connectFuture = bootstrap.connect(address);
        } catch (final Exception e) {
            LOG.info("Failed to connect to {}", address, e);
            setFailure(e);
            return;
        }

        pending = connectFuture;
        // Add listener that attempts reconnect by invoking this method again.
        connectFuture.addListener((ChannelFutureListener) this::channelConnectComplete);
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
        return super.setSuccess(result);
    }

    // Triggered when a connection attempt is resolved.
    private synchronized void channelConnectComplete(final ChannelFuture cf) {
        LOG.debug("Promise {} connection resolved", this);

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
        setFailure(cf.cause());
    }
}
