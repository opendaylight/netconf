/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.channel.Channel;
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
    private static final VarHandle CLOSED;

    static {
        try {
            CLOSED = MethodHandles.lookup().findVarHandle(TransportChannel.class, "closed", boolean.class);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Channel channel;

    private volatile boolean closed;

    protected TransportChannel(final Channel channel) {
        this.channel = requireNonNull(channel);
    }

    /**
     * Return a {@link Future} which completes when this channel is completely closed.
     *
     * @return A {@link Future}
     */
    public final Future<Void> closeFuture() {
        return verifyNotNull(channel.closeFuture());
    }

    /**
     * Access the underlying transport channel.
     *
     * @return The transport channel
     */
    public final Channel channel() {
        // FIXME: IllegalStateException?
        return channel;
    }

    /**
     * Initiate the shutdown of transport from NETCONF layer.
     */
    public final void closeFromNetconf() {
        final var local = releaseChannel();
        if (local != null) {
            closeFromNetconf(local);
        } else {
            LOG.debug("Channel {} already closed", this);
        }
    }

    abstract void closeFromNetconf(Channel channel);

    /**
     * Initiate the shutdown from transport layer. The session becomes
     */
    protected final void closeFromNetty() {
        final var local = releaseChannel();
        if (local != null) {
            closeFromNetty(local);
        } else {
            LOG.debug("Channel {} already closed", this);
        }
    }

    protected abstract void closeFromNetty(Channel channel);

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("channel", channel);
    }

    private @Nullable Channel releaseChannel() {
        return (Channel) CLOSED.getAndSet(this, null);
    }
}
