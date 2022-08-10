/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Mutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A transport-level session. This concept is bound to a {@link Channel} for now, so as to enforce type-safety. It acts
 * as a meeting point between a logical NETCONF session and the underlying transport. Lifecycle is governed via
 * {@link #closeFromNetconf()}, which closes the session from the NETCONF layer and {@link #closeFromNetty()}, which
 * closes the session from the transport layer.
 */
@NonNullByDefault
public abstract class TransportChannel implements Mutable {
    private static final Logger LOG = LoggerFactory.getLogger(TransportChannel.class);
    private static final VarHandle CHANNEL;

    static {
        try {
            CHANNEL = MethodHandles.lookup().findVarHandle(TransportChannel.class, "channel", Channel.class);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ChannelFuture channelClosed;
    private final String str;

    private volatile @Nullable Channel channel;

    protected TransportChannel(final Channel channel) {
        this.channel = requireNonNull(channel);
        channelClosed = channel.closeFuture();
        str = channel.toString();
    }

    /**
     * Access the underlying transport channel.
     *
     * @return The transport channel, or {@code null} if the transport has been shut down.
     */
    public final @Nullable Channel channel() {
        return channel;
    }

    /**
     * Initiate the shutdown of transport from NETCONF layer.
     *
     * @return Future completing when the transport session has been completely closed.
     */
    public final Future<?> closeFromNetconf() {
        final var local = releaseChannel();
        if (local != null) {
            closeFromNetconf(local);
        } else {
            LOG.debug("Channel {} already closed", this);
        }
        return channelClosed;
    }

    abstract void closeFromNetconf(Channel channel);

    /**
     * Initiate the shutdown from transport layer. The session becomes
     */
    protected final void closeFromNetty() {
        final var local = releaseChannel();
        if (local != null) {
            closeFromNetty(local);
        }
    }

    protected abstract void closeFromNetty(Channel channel);

    @Override
    public final String toString() {
        final var local = channel;
        return local != null ? local.toString() : "closed " + str;
    }

    private @Nullable Channel releaseChannel() {
        return (Channel) CHANNEL.getAndSet(this, null);
    }
}
