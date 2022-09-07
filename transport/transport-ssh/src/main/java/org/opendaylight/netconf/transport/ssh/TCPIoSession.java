/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.common.io.IoService;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.common.io.IoWriteFuture;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.Buffer;
import org.opendaylight.netconf.shaded.sshd.common.util.closeable.AbstractCloseable;
import org.opendaylight.netconf.transport.tcp.TCPTransportChannel;

/**
 * An {@link IoSession} backed by a {@link TCPTransportChannel}.
 */
final class TCPIoSession extends AbstractCloseable implements IoSession {
    private static final AtomicLong ID = new AtomicLong();

    private final long id = ID.incrementAndGet();
    private final TCPTransportChannel tcp;

    @GuardedBy("this")
    private Map<Object, Object> attributes;

    private ChannelPromise prev;

    TCPIoSession(final TCPTransportChannel tcp) {
        super(tcp.channel().id().asShortText());
        this.tcp = requireNonNull(tcp);
        prev = newPromise().setSuccess();
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return tcp.channel().remoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return tcp.channel().localAddress();
    }

    @Override
    public SocketAddress getAcceptanceAddress() {
        // FIXME: not entirely accurate
        return null;
    }

    @Override
    public IoWriteFuture writeBuffer(final Buffer buffer) throws IOException {
        final var buf = Unpooled.copiedBuffer(buffer.array(), buffer.rpos(), buffer.available());
        final var msg = new TCPIoWriterFuture(getRemoteAddress());

        final var next = newPromise();
        prev.addListener(ignored -> tcp.channel().writeAndFlush(buf, next));
        prev = next;
        next.addListener(future -> {
            final var cause = future.cause();
            if (cause != null) {
                msg.setValue(cause);
            } else {
                msg.setValue(Boolean.TRUE);
            }
        });
        return msg;
    }

    @Override
    public IoService getService() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void shutdownOutputStream() throws IOException {
        // TODO Auto-generated method stub
    }

    @Override
    public synchronized Object getAttribute(final Object key) {
        return attributes == null ? null : attributes.get(key);
    }

    @Override
    public synchronized Object setAttribute(final Object key, final Object value) {
        return ensureAttributes().put(key, value);
    }

    @Override
    public synchronized Object setAttributeIfAbsent(final Object key, final Object value) {
        return ensureAttributes().putIfAbsent(key, value);
    }

    @Override
    public synchronized Object removeAttribute(final Object key) {
        if (attributes == null) {
            return null;
        }
        final var ret = attributes.remove(key);
        if (attributes.isEmpty()) {
            attributes = null;
        }
        return ret;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("tcp", tcp).toString();
    }

    @Holding("this")
    private @NonNull Map<Object, Object> ensureAttributes() {
        var local = attributes;
        if (local == null) {
            attributes = local = new HashMap<>(4);
        }
        return local;
    }

    private @NonNull ChannelPromise newPromise() {
        return new DefaultChannelPromise(tcp.channel());
    }
}
