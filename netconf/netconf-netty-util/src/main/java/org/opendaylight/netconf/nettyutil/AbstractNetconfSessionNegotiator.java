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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.lock.qual.GuardedBy;
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

    private static final VarHandle STATE;

    static {
        try {
            STATE = MethodHandles.lookup().findVarHandle(AbstractNetconfSessionNegotiator.class, "state", State.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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

    private volatile State state = State.IDLE;

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

    protected final boolean ifNegotiatedAlready() {
        // Indicates whether negotiation already started
        return state != State.IDLE;
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
            changeState(State.IDLE, State.FAILED);
            closeChannel();
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
                changeState(State.OPEN_WAIT, State.FAILED);
            }
        }

        channel.pipeline().addLast(NAME_OF_EXCEPTION_HANDLER, new ExceptionHandlingInboundChannelHandler());

        // Remove special outbound handler for hello message. Insert regular netconf xml message (en|de)coders.
        replaceChannelHandler(channel, AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER,
            new NetconfMessageToXMLEncoder());

        synchronized (this) {
            changeState(State.IDLE, State.OPEN_WAIT);

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

        if (state == State.OPEN_WAIT) {
            LOG.debug("Connection timeout after {}ms, session backed by channel {} is in state {}",
                connectionTimeoutMillis, channel, state);

            // Do not fail negotiation if promise is done or canceled
            // It would result in setting result of the promise second time and that throws exception
            if (!promise.isDone() && !promise.isCancelled()) {
                LOG.warn("Netconf session backed by channel {} was not established after {}", channel,
                    connectionTimeoutMillis);
                changeState(State.OPEN_WAIT, State.FAILED);
                closeChannel();
            }
        } else if (state == State.ESTABLISHED && channel.isOpen()) {
            channel.pipeline().remove(NAME_OF_EXCEPTION_HANDLER);
        }
    }

    private void closeChannel() {
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

    protected final S getSessionForHelloMessage(final NetconfHelloMessage netconfMessage)
            throws NetconfDocumentedException {
        final Document doc = netconfMessage.getDocument();

        if (shouldUseChunkFraming(doc)) {
            insertChunkFramingToPipeline();
        }

        changeState(State.OPEN_WAIT, State.ESTABLISHED);
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

    private static ChannelHandler replaceChannelHandler(final Channel channel, final String handlerKey,
                                                        final ChannelHandler decoder) {
        return channel.pipeline().replace(handlerKey, handlerKey, decoder);
    }

    private void changeState(final State expected, final State target) {
        final var actual = STATE.compareAndExchange(this, expected, target);
        checkState(actual == expected, "Expected state %s does not match actual %s, cannot transition to %s", expected,
            actual, target);
        LOG.debug("Changed state from : {} to : {} for channel: {}", expected, target, channel);
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
        if (state == State.FAILED) {
            // We have already failed -- do not process any more messages
            return;
        }

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
