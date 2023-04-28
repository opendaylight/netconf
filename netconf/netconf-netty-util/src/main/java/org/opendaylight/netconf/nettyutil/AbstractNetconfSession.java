/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import java.io.EOFException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.NetconfExiSession;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfSession;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXICodec;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXIToMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToEXIEncoder;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.EXIException;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.UnsupportedOption;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNetconfSession<S extends NetconfSession, L extends NetconfSessionListener<S>>
        extends SimpleChannelInboundHandler<Object> implements NetconfSession, NetconfExiSession {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfSession.class);

    private final L sessionListener;
    private final @NonNull SessionIdType sessionId;
    private boolean up = false;

    private ChannelHandler delayedEncoder;

    private final Channel channel;

    protected AbstractNetconfSession(final L sessionListener, final Channel channel, final SessionIdType sessionId) {
        this.sessionListener = sessionListener;
        this.channel = channel;
        this.sessionId = requireNonNull(sessionId);
        LOG.debug("Session {} created", sessionId);
    }

    protected abstract S thisInstance();

    @Override
    public void close() {
        channel.close();
        up = false;
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

        final ChannelPromise promise = channel.newPromise();
        channel.eventLoop().execute(() -> {
            channel.writeAndFlush(netconfMessage, promise);
            if (delayedEncoder != null) {
                replaceMessageEncoder(delayedEncoder);
                delayedEncoder = null;
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
    public String toString() {
        final StringBuilder sb = new StringBuilder(getClass().getSimpleName() + "{");
        sb.append("sessionId=").append(sessionId.getValue());
        sb.append(", channel=").append(channel);
        sb.append('}');
        return sb.toString();
    }

    protected final void replaceMessageDecoder(final ChannelHandler handler) {
        replaceChannelHandler(AbstractChannelInitializer.NETCONF_MESSAGE_DECODER, handler);
    }

    protected final void replaceMessageEncoder(final ChannelHandler handler) {
        replaceChannelHandler(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER, handler);
    }

    protected final void replaceMessageEncoderAfterNextMessage(final ChannelHandler handler) {
        delayedEncoder = handler;
    }

    protected final void replaceChannelHandler(final String handlerName, final ChannelHandler handler) {
        channel.pipeline().replace(handlerName, handlerName, handler);
    }

    @Override
    public final void startExiCommunication(final NetconfMessage startExiMessage) {
        final EXIParameters exiParams;
        try {
            exiParams = EXIParameters.fromXmlElement(XmlElement.fromDomDocument(startExiMessage.getDocument()));
        } catch (final UnsupportedOption e) {
            LOG.warn("Unable to parse EXI parameters from {} on session {}", startExiMessage, this, e);
            throw new IllegalArgumentException("Cannot parse options", e);
        }

        final NetconfEXICodec exiCodec = NetconfEXICodec.forParameters(exiParams);
        final NetconfMessageToEXIEncoder exiEncoder = NetconfMessageToEXIEncoder.create(exiCodec);
        final NetconfEXIToMessageDecoder exiDecoder;
        try {
            exiDecoder = NetconfEXIToMessageDecoder.create(exiCodec);
        } catch (EXIException e) {
            LOG.warn("Failed to instantiate EXI decodeer for {} on session {}", exiCodec, this, e);
            throw new IllegalStateException("Cannot instantiate encoder for options", e);
        }

        addExiHandlers(exiDecoder, exiEncoder);
        LOG.debug("Session {} EXI handlers added to pipeline", this);
    }

    /**
     * Add a set encoder/decoder tuple into the channel pipeline as appropriate.
     *
     * @param decoder EXI decoder
     * @param encoder EXI encoder
     */
    protected abstract void addExiHandlers(ByteToMessageDecoder decoder, MessageToByteEncoder<NetconfMessage> encoder);

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
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to delegate channel inactive event on channel " + ctx.channel(), e);
        }
    }

    @Override
    protected final void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
        LOG.debug("Message was received: {}", msg);
        if (msg instanceof NetconfMessage message) {
            handleMessage(message);
        } else if (msg instanceof Exception failure) {
            handleError(failure);
        } else {
            LOG.warn("Ignoring unexpected message {}", msg);
        }
    }

    @Override
    public final void handlerAdded(final ChannelHandlerContext ctx) {
        sessionUp();
    }
}
