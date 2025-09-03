/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import java.net.SocketAddress;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.session.helpers.MissingAttachedSessionException;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoService;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoSession;

final class TransportIoSession extends NettyIoSession {
    TransportIoSession(final TransportIoService service, final IoHandler handler,
            final SocketAddress acceptanceAddress) {
        super(service, handler, acceptanceAddress);
    }

    ChannelInboundHandler handler() {
        return adapter;
    }

    /**
     * Adapted version of {@link NettyIoSession#channelActive(ChannelHandlerContext)}.
     *
     * <p>We do not need to use {@link NettyIoService#channelGroup} as we track the underlying transport
     * instead, hence any channels tracked here will disappear when we shut down the underlay.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void channelActive(final ChannelHandlerContext ctx) throws Exception {
        context = ctx;
        final var channel = ctx.channel();
        prev = context.newPromise().setSuccess();
        remoteAddr = channel.remoteAddress();
        // If handler.sessionCreated() propagates an exception, we'll have a NettyIoSession without SSH session. We'll
        // propagate the exception, exceptionCaught will be called, which won't find an SSH session to handle the
        // exception and propagate a MissingAttachedSessionException. However, Netty will swallow and log exceptions
        // propagated out of exceptionCaught. This will lead to follow-up exceptions.
        //
        // We have to close the NettyIoSession in this case.
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
        } catch (final Throwable e) {
            warn("channelActive(session={}): could not create SSH session ({}); closing",
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

    /**
     * Adapted version of {@link NettyIoSession#channelInactive(ChannelHandlerContext)}.
     *
     * <p>We do not need to use {@link NettyIoService#channelGroup} as we track the underlying transport
     * instead, hence any channels tracked here will disappear when we shut down the underlay.
     */
    @Override
    protected void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        try {
            handler.sessionClosed(this);
        } catch (final MissingAttachedSessionException e) {
            // handler.sessionClosed() is supposed to close the attached SSH session. If there isn't one,
            // we don't care anymore at this point.
            if (log.isTraceEnabled()) {
                log.trace("channelInactive(session={}): caught {}", this, e.getClass().getName(), e);
            }
        } finally {
            final var channel = ctx.channel();
            final var connectFuture = channel.attr(NettyIoService.CONNECT_FUTURE_KEY);
            final var future = connectFuture.get();
            if (future != null) {
                // If the future wasn't fulfilled already cancel it.
                final var cancellation = future.cancel();
                if (cancellation != null) {
                    cancellation.setCanceled();
                }
            }
            context = null;
            // adapter is not propagating fireChannelInactive() down the the pipeline, but instead loops here.
            // Once we have cleaned up, propagate fireChannelInactive() ourselves.
            ctx.fireChannelInactive();
        }
    }
}
