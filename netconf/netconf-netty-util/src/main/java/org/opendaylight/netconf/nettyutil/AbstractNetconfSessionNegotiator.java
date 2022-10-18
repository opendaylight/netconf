/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.messages.NetconfHelloMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.nettyutil.handler.FramingMechanismHandlerFactory;
import org.opendaylight.netconf.nettyutil.handler.NetconfChunkAggregator;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToXMLEncoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToHelloMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToMessageDecoder;
import org.opendaylight.netconf.util.messages.FramingMechanism;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public abstract class AbstractNetconfSessionNegotiator<S extends AbstractNetconfSession<S, L>,
            L extends NetconfSessionListener<S>>
            extends ChannelInboundHandlerAdapter implements NetconfSessionNegotiator<S> {
    /**
     * Possible states for Finite State Machine.
     */
    protected enum State {
        IDLE, OPEN_WAIT, FAILED, ESTABLISHED
    }

    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfSessionNegotiator.class);
    private static final String NAME_OF_EXCEPTION_HANDLER = "lastExceptionHandler";
    private static final String DEFAULT_MAXIMUM_CHUNK_SIZE_PROP = "org.opendaylight.netconf.default.maximum.chunk.size";
    private static final int DEFAULT_MAXIMUM_CHUNK_SIZE_DEFAULT = 16 * 1024 * 1024;

    /**
     * Default upper bound on the size of an individual chunk. This value can be controlled through
     * {@value #DEFAULT_MAXIMUM_CHUNK_SIZE_PROP} system property and defaults to
     * {@value #DEFAULT_MAXIMUM_CHUNK_SIZE_DEFAULT} bytes.
     */
    @Beta
    public static final @NonNegative int DEFAULT_MAXIMUM_INCOMING_CHUNK_SIZE;

    static {
        final int propValue = Integer.getInteger(DEFAULT_MAXIMUM_CHUNK_SIZE_PROP, DEFAULT_MAXIMUM_CHUNK_SIZE_DEFAULT);
        if (propValue <= 0) {
            LOG.warn("Ignoring invalid {} value {}", DEFAULT_MAXIMUM_CHUNK_SIZE_PROP, propValue);
            DEFAULT_MAXIMUM_INCOMING_CHUNK_SIZE = DEFAULT_MAXIMUM_CHUNK_SIZE_DEFAULT;
        } else {
            DEFAULT_MAXIMUM_INCOMING_CHUNK_SIZE = propValue;
        }
        LOG.debug("Default maximum incoming NETCONF chunk size is {} bytes", DEFAULT_MAXIMUM_INCOMING_CHUNK_SIZE);
    }

    private final @NonNull NetconfHelloMessage localHello;
    protected final Channel channel;

    private final @NonNegative int maximumIncomingChunkSize;
    private final long connectionTimeoutMillis;
    private final Promise<S> promise;
    private final L sessionListener;
    private final Timer timer;

    @GuardedBy("this")
    private Timeout timeoutTask;
    @GuardedBy("this")
    private State state = State.IDLE;

    protected AbstractNetconfSessionNegotiator(final NetconfHelloMessage hello, final Promise<S> promise,
                                               final Channel channel, final Timer timer, final L sessionListener,
                                               final long connectionTimeoutMillis,
                                               final @NonNegative int maximumIncomingChunkSize) {
        this.localHello = requireNonNull(hello);
        this.promise = requireNonNull(promise);
        this.channel = requireNonNull(channel);
        this.timer = timer;
        this.sessionListener = sessionListener;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.maximumIncomingChunkSize = maximumIncomingChunkSize;
        checkArgument(maximumIncomingChunkSize > 0, "Invalid maximum incoming chunk size %s", maximumIncomingChunkSize);
    }

    @Deprecated(since = "4.0.1", forRemoval = true)
    protected AbstractNetconfSessionNegotiator(final NetconfHelloMessage hello, final Promise<S> promise,
                                               final Channel channel, final Timer timer,
                                               final L sessionListener, final long connectionTimeoutMillis) {
        this(hello, promise, channel, timer, sessionListener, connectionTimeoutMillis,
            DEFAULT_MAXIMUM_INCOMING_CHUNK_SIZE);
    }

    protected final @NonNull NetconfHelloMessage localHello() {
        return localHello;
    }

    protected final void startNegotiation() {
        if (ifNegotiatedAlready()) {
            LOG.debug("Negotiation on channel {} already started", channel);
        } else {
            final var sslHandler = getSslHandler(channel);
            if (sslHandler != null) {
                sslHandler.handshakeFuture().addListener(future -> {
                    checkState(future.isSuccess(), "Ssl handshake was not successful");
                    LOG.debug("Ssl handshake complete");
                    start();
                });
            } else {
                start();
            }
        }
    }

    protected final synchronized boolean ifNegotiatedAlready() {
        // Indicates whether negotiation already started
        return this.state != State.IDLE;
    }

    private static @Nullable SslHandler getSslHandler(final Channel channel) {
        return channel.pipeline().get(SslHandler.class);
    }

    private void start() {
        LOG.debug("Session negotiation started with hello message {} on channel {}", localHello, channel);

        // Send the message out, but to not run listeners just yet, as we have some more state transitions to go through
        final var helloFuture = channel.writeAndFlush(localHello);

        channel.pipeline().addLast(NAME_OF_EXCEPTION_HANDLER, new ExceptionHandlingInboundChannelHandler());

        replaceHelloMessageOutboundHandler();

        synchronized (this) {
            lockedChangeState(State.OPEN_WAIT);

            // Service the timeout on channel's eventloop, so that we do not get state transition problems
            timeoutTask = timer.newTimeout(unused -> channel.eventLoop().execute(this::timeoutExpired),
                connectionTimeoutMillis, TimeUnit.MILLISECONDS);
        }

        // State transition completed, now run any additional processing
        helloFuture.addListener(this::onHelloWriteComplete);
    }

    private void onHelloWriteComplete(final Future<?> future) {
        final var cause = future.cause();
        if (cause != null) {
            LOG.info("Failed to send message {} on channel {}", localHello, channel, cause);
            negotiationFailed(cause);
        } else {
            LOG.trace("Message {} sent to socket on channel {}", localHello, channel);
        }
    }

    private synchronized void timeoutExpired() {
        if (timeoutTask == null) {
            // cancelTimeout() between expiry and execution on the loop
            return;
        }
        timeoutTask = null;

        if (state != State.ESTABLISHED) {
            LOG.debug("Connection timeout after {}ms, session backed by channel {} is in state {}",
                connectionTimeoutMillis, channel, state);

            // Do not fail negotiation if promise is done or canceled
            // It would result in setting result of the promise second time and that throws exception
            if (!promise.isDone() && !promise.isCancelled()) {
                LOG.warn("Netconf session backed by channel {} was not established after {}", channel,
                    connectionTimeoutMillis);
                changeState(State.FAILED);

                channel.close().addListener(future -> {
                    final var cause = future.cause();
                    if (cause != null) {
                        LOG.warn("Channel {} closed: fail", channel, cause);
                    } else {
                        LOG.debug("Channel {} closed: success", channel);
                    }
                });
            }
        } else if (channel.isOpen()) {
            channel.pipeline().remove(NAME_OF_EXCEPTION_HANDLER);
        }
    }

    private synchronized void cancelTimeout() {
        if (timeoutTask != null && !timeoutTask.cancel()) {
            // Late-coming cancel: make sure the task does not actually run
            timeoutTask = null;
        }
    }

    protected final S getSessionForHelloMessage(final NetconfHelloMessage netconfMessage)
            throws NetconfDocumentedException {
        final Document doc = netconfMessage.getDocument();

        if (shouldUseChunkFraming(doc)) {
            insertChunkFramingToPipeline();
        }

        changeState(State.ESTABLISHED);
        return getSession(sessionListener, channel, netconfMessage);
    }

    protected abstract S getSession(L sessionListener, Channel channel, NetconfHelloMessage message)
        throws NetconfDocumentedException;

    /**
     * Insert chunk framing handlers into the pipeline.
     */
    private void insertChunkFramingToPipeline() {
        replaceChannelHandler(channel, AbstractChannelInitializer.NETCONF_MESSAGE_FRAME_ENCODER,
                FramingMechanismHandlerFactory.createHandler(FramingMechanism.CHUNK));
        replaceChannelHandler(channel, AbstractChannelInitializer.NETCONF_MESSAGE_AGGREGATOR,
                new NetconfChunkAggregator(maximumIncomingChunkSize));
    }

    private boolean shouldUseChunkFraming(final Document doc) {
        return containsBase11Capability(doc) && containsBase11Capability(localHello.getDocument());
    }

    /**
     * Remove special inbound handler for hello message. Insert regular netconf xml message (en|de)coders.
     *
     * <p>
     * Inbound hello message handler should be kept until negotiation is successful
     * It caches any non-hello messages while negotiation is still in progress
     */
    protected final void replaceHelloMessageInboundHandler(final S session) {
        ChannelHandler helloMessageHandler = replaceChannelHandler(channel,
                AbstractChannelInitializer.NETCONF_MESSAGE_DECODER, new NetconfXMLToMessageDecoder());

        checkState(helloMessageHandler instanceof NetconfXMLToHelloMessageDecoder,
                "Pipeline handlers misplaced on session: %s, pipeline: %s", session, channel.pipeline());
        Iterable<NetconfMessage> netconfMessagesFromNegotiation =
                ((NetconfXMLToHelloMessageDecoder) helloMessageHandler).getPostHelloNetconfMessages();

        // Process messages received during negotiation
        // The hello message handler does not have to be synchronized,
        // since it is always call from the same thread by netty.
        // It means, we are now using the thread now
        for (NetconfMessage message : netconfMessagesFromNegotiation) {
            session.handleMessage(message);
        }
    }

    /**
     * Remove special outbound handler for hello message. Insert regular netconf xml message (en|de)coders.
     */
    private void replaceHelloMessageOutboundHandler() {
        replaceChannelHandler(channel, AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER,
                new NetconfMessageToXMLEncoder());
    }

    private static ChannelHandler replaceChannelHandler(final Channel channel, final String handlerKey,
                                                        final ChannelHandler decoder) {
        return channel.pipeline().replace(handlerKey, handlerKey, decoder);
    }

    private synchronized void changeState(final State newState) {
        lockedChangeState(newState);
    }

    @Holding("this")
    private void lockedChangeState(final State newState) {
        LOG.debug("Changing state from : {} to : {} for channel: {}", state, newState, channel);
        checkState(isStateChangePermitted(state, newState),
                "Cannot change state from %s to %s for channel %s", state, newState, channel);
        this.state = newState;
    }

    private static boolean containsBase11Capability(final Document doc) {
        final NodeList nList = doc.getElementsByTagNameNS(
            XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0,
            XmlNetconfConstants.CAPABILITY);
        for (int i = 0; i < nList.getLength(); i++) {
            if (nList.item(i).getTextContent().contains(XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStateChangePermitted(final State state, final State newState) {
        if (state == State.IDLE && newState == State.OPEN_WAIT) {
            return true;
        }
        if (state == State.OPEN_WAIT && newState == State.ESTABLISHED) {
            return true;
        }
        if (state == State.OPEN_WAIT && newState == State.FAILED) {
            return true;
        }
        LOG.debug("Transition from {} to {} is not allowed", state, newState);
        return false;
    }

    /**
     * Handler to catch exceptions in pipeline during negotiation.
     */
    private final class ExceptionHandlingInboundChannelHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            LOG.warn("An exception occurred during negotiation with {} on channel {}",
                    channel.remoteAddress(), channel, cause);
            // FIXME: this is quite suspect as it is competing with timeoutExpired() without synchronization
            cancelTimeout();
            negotiationFailed(cause);
            changeState(State.FAILED);
        }
    }

    protected final void negotiationSuccessful(final S session) {
        LOG.debug("Negotiation on channel {} successful with session {}", channel, session);
        channel.pipeline().replace(this, "session", session);
        promise.setSuccess(session);
    }

    protected void negotiationFailed(final Throwable cause) {
        LOG.debug("Negotiation on channel {} failed", channel, cause);
        channel.close();
        promise.setFailure(cause);
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    public final void channelActive(final ChannelHandlerContext ctx) {
        LOG.debug("Starting session negotiation on channel {}", channel);
        try {
            startNegotiation();
        } catch (final Exception e) {
            LOG.warn("Unexpected negotiation failure on channel {}", channel, e);
            negotiationFailed(e);
        }
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    public final void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        LOG.debug("Negotiation read invoked on channel {}", channel);
        try {
            handleMessage((NetconfHelloMessage) msg);
        } catch (final Exception e) {
            LOG.debug("Unexpected error while handling negotiation message {} on channel {}", msg, channel, e);
            negotiationFailed(e);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        LOG.info("Unexpected error during negotiation on channel {}", channel, cause);
        negotiationFailed(cause);
    }

    protected abstract void handleMessage(NetconfHelloMessage msg) throws Exception;
}
