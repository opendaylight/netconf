/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.io.EOFException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.NetconfSession;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNetconfSession<S extends NetconfSession, L extends NetconfSessionListener<S>>
        extends SimpleChannelInboundHandler<Object> implements NetconfSession {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfSession.class);

    private final @NonNull SessionIdType sessionId;
    private final L sessionListener;
    private final Channel channel;

    private Runnable runAfterNextMessage;
    private boolean up;

    protected AbstractNetconfSession(final L sessionListener, final Channel channel, final SessionIdType sessionId) {
        this.sessionListener = sessionListener;
        this.channel = channel;
        this.sessionId = requireNonNull(sessionId);
        LOG.debug("Session {} created", sessionId);
    }

    protected abstract S thisInstance();

    @Override
    public void close() {
        up = false;
        channel.close();
        sessionListener.onSessionTerminated(thisInstance(), new NetconfTerminationReason("Session closed"));
    }

    protected void handleMessage(final NetconfMessage netconfMessage) {
        LOG.debug("handling incoming message");
        sessionListener.onMessage(thisInstance(), netconfMessage);
    }

    protected void handleError(final Exception failure) {
        LOG.debug("handling incoming error");
        sessionListener.onError(thisInstance(), failure);
    }

    @Override
    public ChannelFuture sendMessage(final NetconfMessage netconfMessage) {
        // From: https://github.com/netty/netty/issues/3887
        // Netty can provide "ordering" in the following situations:
        // 1. You are doing all writes from the EventLoop thread; OR
        // 2. You are doing no writes from the EventLoop thread (i.e. all writes are being done in other thread(s)).
        //
        // Restconf writes to a netconf mountpoint execute multiple messages
        // and one of these was executed from a restconf thread thus breaking ordering so
        // we need to execute all messages from an EventLoop thread.

        final var promise = channel.newPromise();
        channel.eventLoop().execute(() -> {
            channel.writeAndFlush(netconfMessage, promise);

            final var local = runAfterNextMessage;
            if (local != null) {
                runAfterNextMessage = null;
                local.run();
            }
        });

        return promise;
    }

    protected void endOfInput() {
        LOG.debug("Session {} end of input detected while session was in state {}", this, up ? "up" : "initialized");
        if (up) {
            sessionListener.onSessionDown(thisInstance(), new EOFException("End of input"));
        }
    }

    protected void sessionUp() {
        LOG.debug("Session {} up", this);
        sessionListener.onSessionUp(thisInstance());
        up = true;
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("sessionId", sessionId.getValue()).add("channel", channel);
    }

    protected final void runAfterNextMessage(final Runnable runnable) {
        runAfterNextMessage = requireNonNull(runnable);
    }

    protected final <T extends ChannelHandler>void replaceChannelHandler(final Class<T> type, final String name,
            final T handler) {
        channel.pipeline().replace(type, name, handler);
    }

    public final boolean isUp() {
        return up;
    }

    public final @NonNull SessionIdType sessionId() {
        return sessionId;
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    public final void channelInactive(final ChannelHandlerContext ctx) {
        LOG.debug("Channel {} inactive.", ctx.channel());
        endOfInput();
        try {
            // Forward channel inactive event, all handlers in pipeline might be interested in the event e.g. close
            // channel handler of reconnect promise
            super.channelInactive(ctx);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delegate channel inactive event on channel " + ctx.channel(), e);
        }
    }

    @Override
    protected final void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
        LOG.debug("Message was received: {}", msg);
        switch (msg) {
            case NetconfMessage message -> handleMessage(message);
            case Exception failure -> handleError(failure);
            default -> {
                LOG.warn("Ignoring unexpected message {}", msg);
            }
        }
    }

    @Override
    public final void handlerAdded(final ChannelHandlerContext ctx) {
        sessionUp();
    }
}
