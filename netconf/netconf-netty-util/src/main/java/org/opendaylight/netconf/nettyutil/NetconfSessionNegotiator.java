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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.codec.ChunkedFrameDecoder;
import org.opendaylight.netconf.codec.FrameDecoder;
import org.opendaylight.netconf.codec.FramingSupport;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.codec.XMLMessageDecoder;
import org.opendaylight.netconf.codec.XMLMessageWriter;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.nettyutil.handler.HelloXMLMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * A negotiator of a NETCONF session. It is responsible for message handling while the exact session parameters are not
 * known. Once the session parameters are finalized, the negotiator replaces itself in the channel pipeline with the
 * session.
 *
 * @param <S> Session type
 * @param <L> Session listener type
 */
public abstract class NetconfSessionNegotiator<S extends AbstractNetconfSession<S, L>,
            L extends NetconfSessionListener<S>> extends ChannelInboundHandlerAdapter {
    /**
     * Possible states for Finite State Machine.
     */
    protected enum State {
        IDLE, OPEN_WAIT, FAILED, ESTABLISHED
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfSessionNegotiator.class);
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

    private final @NonNull HelloMessage localHello;
    protected final @NonNull Channel channel;

    private final @NonNegative int maximumIncomingChunkSize;
    private final long connectionTimeoutMillis;
    private final @NonNull Promise<S> promise;
    private final @NonNull L sessionListener;
    private final @NonNull NetconfTimer timer;

    @GuardedBy("this")
    private Timeout timeoutTask;
    @GuardedBy("this")
    private State state = State.IDLE;

    protected NetconfSessionNegotiator(final HelloMessage hello, final Promise<S> promise, final Channel channel,
            final NetconfTimer timer, final L sessionListener, final long connectionTimeoutMillis,
            final @NonNegative int maximumIncomingChunkSize) {
        localHello = requireNonNull(hello);
        this.promise = requireNonNull(promise);
        this.channel = requireNonNull(channel);
        this.timer = requireNonNull(timer);
        this.sessionListener = requireNonNull(sessionListener);
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.maximumIncomingChunkSize = maximumIncomingChunkSize;
        checkArgument(maximumIncomingChunkSize > 0, "Invalid maximum incoming chunk size %s", maximumIncomingChunkSize);
    }

    protected final @NonNull HelloMessage localHello() {
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

    protected final boolean ifNegotiatedAlready() {
        // Indicates whether negotiation already started
        return state() != State.IDLE;
    }

    private synchronized State state() {
        return state;
    }

    private static @Nullable SslHandler getSslHandler(final Channel channel) {
        return channel.pipeline().get(SslHandler.class);
    }

    private void start() {
        LOG.debug("Sending negotiation proposal {} on channel {}", localHello, channel);

        // Send the message out, but to not run listeners just yet, as we have some more state transitions to go through
        final var helloFuture = channel.writeAndFlush(localHello);

        // Quick check: if the future has already failed we call it quits before negotiation even started
        final var helloCause = helloFuture.cause();
        if (helloCause != null) {
            LOG.warn("Failed to send negotiation proposal on channel {}", channel, helloCause);
            failAndClose();
            return;
        }

        // Catch any exceptions from this point on. Use a named class to ease debugging.
        final class ExceptionHandlingInboundChannelHandler extends ChannelInboundHandlerAdapter {
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

        channel.pipeline().addLast(NAME_OF_EXCEPTION_HANDLER, new ExceptionHandlingInboundChannelHandler());

        // Remove special outbound handler for hello message. Insert regular netconf xml message (en|de)coders.
        channel.pipeline().get(MessageEncoder.class).setWriter(XMLMessageWriter.pretty());

        synchronized (this) {
            lockedChangeState(State.OPEN_WAIT);

            // Service the timeout on channel's eventloop, so that we do not get state transition problems
            timeoutTask = timer.newTimeout(unused -> channel.eventLoop().execute(this::timeoutExpired),
                connectionTimeoutMillis, TimeUnit.MILLISECONDS);
        }

        LOG.debug("Session negotiation started on channel {}", channel);

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
                failAndClose();
            }
        } else if (channel.isOpen()) {
            channel.pipeline().remove(NAME_OF_EXCEPTION_HANDLER);
        }
    }

    private void failAndClose() {
        changeState(State.FAILED);
        channel.close().addListener(this::onChannelClosed);
    }

    private void onChannelClosed(final Future<?> future) {
        final var cause = future.cause();
        if (cause != null) {
            LOG.warn("Channel {} closed: fail", channel, cause);
        } else {
            LOG.debug("Channel {} closed: success", channel);
        }
    }

    private synchronized void cancelTimeout() {
        if (timeoutTask != null && !timeoutTask.cancel()) {
            // Late-coming cancel: make sure the task does not actually run
            timeoutTask = null;
        }
    }

    protected final S getSessionForHelloMessage(final HelloMessage netconfMessage)
            throws NetconfDocumentedException {
        final Document doc = netconfMessage.getDocument();

        if (shouldUseChunkFraming(doc)) {
            insertChunkFramingToPipeline();
        }

        changeState(State.ESTABLISHED);
        return getSession(sessionListener, channel, netconfMessage);
    }

    protected abstract S getSession(L sessionListener, Channel channel, HelloMessage message)
        throws NetconfDocumentedException;

    /**
     * Insert chunk framing handlers into the pipeline.
     */
    private void insertChunkFramingToPipeline() {
        channel.pipeline().get(MessageEncoder.class).setFraming(FramingSupport.chunk());
        replaceChannelHandler(channel, FrameDecoder.HANDLER_NAME, new ChunkedFrameDecoder(maximumIncomingChunkSize));
    }

    private boolean shouldUseChunkFraming(final Document doc) {
        return containsBase11Capability(doc) && containsBase11Capability(localHello.getDocument());
    }

    /**
     * Remove special inbound handler for hello message. Insert regular netconf xml message (en|de)coders.
     *
     * <p>Inbound hello message handler should be kept until negotiation is successful.
     * It caches any non-hello messages while negotiation is still in progress
     */
    protected final void replaceHelloMessageInboundHandler(final S session) {
        final var helloMessageHandler = replaceChannelHandler(channel, MessageDecoder.HANDLER_NAME,
            new XMLMessageDecoder());
        if (!(helloMessageHandler instanceof HelloXMLMessageDecoder helloDecorder)) {
            throw new IllegalStateException(
                "Pipeline handlers misplaced on session: " + session + ", pipeline: " + channel.pipeline());
        }

        // Process messages received during negotiation
        // The hello message handler does not have to be synchronized,
        // since it is always call from the same thread by netty.
        // It means, we are now using the thread now
        for (NetconfMessage message : helloDecorder.getPostHelloNetconfMessages()) {
            session.handleMessage(message);
        }
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
        state = newState;
    }

    private static boolean containsBase11Capability(final Document doc) {
        final NodeList nList = doc.getElementsByTagNameNS(NamespaceURN.BASE, XmlNetconfConstants.CAPABILITY);
        for (int i = 0; i < nList.getLength(); i++) {
            if (nList.item(i).getTextContent().contains(CapabilityURN.BASE_1_1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStateChangePermitted(final State state, final State newState) {
        if (state == State.IDLE && (newState == State.OPEN_WAIT || newState == State.FAILED)) {
            return true;
        }
        if (state == State.OPEN_WAIT && (newState == State.ESTABLISHED || newState == State.FAILED)) {
            return true;
        }
        LOG.debug("Transition from {} to {} is not allowed", state, newState);
        return false;
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
        if (state() == State.FAILED) {
            // We have already failed -- do not process any more messages
            return;
        }

        LOG.debug("Negotiation read invoked on channel {}", channel);
        try {
            handleMessage((HelloMessage) msg);
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

    protected abstract void handleMessage(HelloMessage msg) throws Exception;
}
