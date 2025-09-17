/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DuplexChannel;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.shaded.sshd.common.io.AbstractIoWriteFuture;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.io.IoService;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.common.io.IoWriteFuture;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.Buffer;
import org.opendaylight.netconf.shaded.sshd.common.util.closeable.AbstractCloseable;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class TransportIoSession extends AbstractCloseable implements IoSession {
    private static final Logger LOG = LoggerFactory.getLogger(TransportIoSession.class);

    private final Map<Object, Object> attributes = new HashMap<>();
    private final AtomicBoolean readSuspended = new AtomicBoolean();

    private final IoHandler handler;
    private final SocketAddress acceptanceAddress;
    private final long id;

    private ChannelHandlerContext context;
    private SocketAddress remoteAddr;
    private ChannelFuture prev;

    TransportIoSession(final IoHandler handler, final SocketAddress acceptanceAddress, final long id) {
        this.handler = requireNonNull(handler);
        this.acceptanceAddress = requireNonNull(acceptanceAddress);
        this.id = id;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public SocketAddress getAcceptanceAddress() {
        return acceptanceAddress;
    }

    @Override
    public Object getAttribute(final Object key) {
        synchronized (attributes) {
            return attributes.get(key);
        }
    }

    @Override
    public Object setAttribute(final Object key, final Object value) {
        synchronized (attributes) {
            return attributes.put(key, value);
        }
    }

    @Override
    public Object setAttributeIfAbsent(final Object key, final Object value) {
        synchronized (attributes) {
            return attributes.putIfAbsent(key, value);
        }
    }

    @Override
    public Object removeAttribute(final Object key) {
        synchronized (attributes) {
            return attributes.remove(key);
        }
    }

    @Override
    public IoWriteFuture writeBuffer(final Buffer buffer) throws IOException {
        int bufLen = buffer.available();
        ByteBuf buf = Unpooled.buffer(bufLen);
        buf.writeBytes(buffer.array(), buffer.rpos(), bufLen);
        DefaultIoWriteFuture msg = new DefaultIoWriteFuture(getRemoteAddress(), null);
        ChannelHandlerContext ctx = context;
        if (ctx == null) {
            msg.setValue(new ClosedChannelException());
            return msg;
        }
        ChannelPromise next = ctx.newPromise();
        prev.addListener(whatever -> {
            ChannelHandlerContext localCtx = context;
            if (localCtx != null) {
                localCtx.writeAndFlush(buf, next);
            } else {
                msg.setValue(new ClosedChannelException());
                next.cancel(true);
            }
        });
        prev = next;
        next.addListener(fut -> {
            if (fut.isSuccess()) {
                msg.setValue(Boolean.TRUE);
            } else {
                msg.setValue(fut.cause());
            }
        });
        return msg;
    }

    private static class DefaultIoWriteFuture extends AbstractIoWriteFuture {
        DefaultIoWriteFuture(Object id, Object lock) {
            super(id, lock);
        }
    }

    @Override
    public IoService getService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdownOutputStream() throws IOException {
        ChannelHandlerContext ctx = context;
        if (ctx == null) {
            return;
        }
        Channel ch = ctx.channel();
        if (ch instanceof DuplexChannel) {
            ((DuplexChannel) ch).shutdownOutput();
        } else if (log.isDebugEnabled()) {
            log.debug("shutdownOutputStream({}) channel is not DuplexChannel: {}", this,
                (ch == null) ? null : ch.getClass().getSimpleName());
        }
    }

    @Override
    public void suspendRead() {
        if (!readSuspended.getAndSet(true)) {
            ChannelHandlerContext ctx = context;
            if (ctx != null) {
                Channel ch = ctx.channel();
                ch.config().setAutoRead(false);
            }
        }
    }

    @Override
    public void resumeRead() {
        if (readSuspended.getAndSet(false)) {
            ChannelHandlerContext ctx = context;
            if (ctx != null) {
                Channel ch = ctx.channel();
                ch.config().setAutoRead(true);
            }
        }
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddr;
    }

    @Override
    public SocketAddress getLocalAddress() {
        Channel channel = (context == null) ? null : context.channel();
        return (channel == null) ? null : channel.localAddress();
    }

    @SuppressWarnings("checkstyle:illegalCatch")
    public void channelActive(final ChannelHandlerContext ctx) {
        context = ctx;
        final var channel = context.channel();
        remoteAddr = channel.remoteAddress();
        prev = context.newPromise().setSuccess();
        final var connectFuture = channel.attr(NettyIoService.CONNECT_FUTURE_KEY);
        final var future = connectFuture.get();
        try {
            handler.sessionCreated(this);
            if (future != null) {
                future.setSession(this);
                if (future.getSession() != this) {
                    close(true);
                }
            }
        } catch (Throwable e) {
            LOG.warn("channelActive(session={}): could not create SSH session ({}); closing",
                this, e.getClass().getName(), e);
            try {
                if (future != null) {
                    future.setException(e);
                }
            } finally {
                close(true);
            }
        }
    }
}
