/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Optional;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import java.util.concurrent.TimeUnit;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.NetconfSessionPreferences;
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

public abstract class AbstractNetconfSessionNegotiator<P extends NetconfSessionPreferences,
        S extends AbstractNetconfSession<S, L>, L extends NetconfSessionListener<S>>
            extends ChannelInboundHandlerAdapter implements NetconfSessionNegotiator<S> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfSessionNegotiator.class);

    public static final String NAME_OF_EXCEPTION_HANDLER = "lastExceptionHandler";

    protected final P sessionPreferences;
    protected final Channel channel;

    private final Promise<S> promise;
    private final L sessionListener;
    private Timeout timeout;

    /**
     * Possible states for Finite State Machine.
     */
    protected enum State {
        IDLE, OPEN_WAIT, FAILED, ESTABLISHED
    }

    private State state = State.IDLE;
    private final Timer timer;
    private final long connectionTimeoutMillis;

    protected AbstractNetconfSessionNegotiator(final P sessionPreferences, final Promise<S> promise,
                                               final Channel channel, final Timer timer,
                                               final L sessionListener, final long connectionTimeoutMillis) {
        this.channel = requireNonNull(channel);
        this.promise = requireNonNull(promise);
        this.sessionPreferences = sessionPreferences;
        this.timer = timer;
        this.sessionListener = sessionListener;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }

    protected final void startNegotiation() {
        if (ifNegotiatedAlready()) {
            LOG.debug("Negotiation on channel {} already started", channel);
        } else {
            final Optional<SslHandler> sslHandler = getSslHandler(channel);
            if (sslHandler.isPresent()) {
                sslHandler.get().handshakeFuture().addListener(future -> {
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

    private static Optional<SslHandler> getSslHandler(final Channel channel) {
        final SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
        return sslHandler == null ? Optional.absent() : Optional.of(sslHandler);
    }

    public P getSessionPreferences() {
        return sessionPreferences;
    }

    private void start() {
        final NetconfHelloMessage helloMessage = this.sessionPreferences.getHelloMessage();
        LOG.debug("Session negotiation started with hello message {} on channel {}", helloMessage, channel);

        channel.pipeline().addLast(NAME_OF_EXCEPTION_HANDLER, new ExceptionHandlingInboundChannelHandler());

        sendMessage(helloMessage);

        replaceHelloMessageOutboundHandler();
        changeState(State.OPEN_WAIT);

        timeout = this.timer.newTimeout(new TimerTask() {
            @Override
            @SuppressWarnings("checkstyle:hiddenField")
            public void run(final Timeout timeout) {
                synchronized (this) {
                    if (state != State.ESTABLISHED) {

                        LOG.debug("Connection timeout after {}, session is in state {}", timeout, state);

                        // Do not fail negotiation if promise is done or canceled
                        // It would result in setting result of the promise second time and that throws exception
                        if (!isPromiseFinished()) {
                            LOG.warn("Netconf session was not established after {}", connectionTimeoutMillis);
                            changeState(State.FAILED);

                            channel.close().addListener((GenericFutureListener<ChannelFuture>) future -> {
                                if (future.isSuccess()) {
                                    LOG.debug("Channel {} closed: success", future.channel());
                                } else {
                                    LOG.warn("Channel {} closed: fail", future.channel());
                                }
                            });
                        }
                    } else if (channel.isOpen()) {
                        channel.pipeline().remove(NAME_OF_EXCEPTION_HANDLER);
                    }
                }
            }

            private boolean isPromiseFinished() {
                return promise.isDone() || promise.isCancelled();
            }

        }, connectionTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private void cancelTimeout() {
        if (timeout != null) {
            timeout.cancel();
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

    /**
     * Insert chunk framing handlers into the pipeline.
     */
    private void insertChunkFramingToPipeline() {
        replaceChannelHandler(channel, AbstractChannelInitializer.NETCONF_MESSAGE_FRAME_ENCODER,
                FramingMechanismHandlerFactory.createHandler(FramingMechanism.CHUNK));
        replaceChannelHandler(channel, AbstractChannelInitializer.NETCONF_MESSAGE_AGGREGATOR,
                new NetconfChunkAggregator());
    }

    private boolean shouldUseChunkFraming(final Document doc) {
        return containsBase11Capability(doc)
                && containsBase11Capability(sessionPreferences.getHelloMessage().getDocument());
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

    @SuppressWarnings("checkstyle:hiddenField")
    protected abstract S getSession(L sessionListener, Channel channel, NetconfHelloMessage message)
            throws NetconfDocumentedException;

    private synchronized void changeState(final State newState) {
        LOG.debug("Changing state from : {} to : {} for channel: {}", state, newState, channel);
        checkState(isStateChangePermitted(state, newState),
                "Cannot change state from %s to %s for chanel %s", state, newState, channel);
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
            LOG.warn("An exception occurred during negotiation with {}", channel.remoteAddress(), cause);
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

    /**
     * Send a message to peer and fail negotiation if it does not reach
     * the peer.
     *
     * @param msg Message which should be sent.
     */
    protected final void sendMessage(final NetconfMessage msg) {
        this.channel.writeAndFlush(msg).addListener(f -> {
            if (!f.isSuccess()) {
                LOG.info("Failed to send message {}", msg, f.cause());
                negotiationFailed(f.cause());
            } else {
                LOG.trace("Message {} sent to socket", msg);
            }
        });
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    public final void channelActive(final ChannelHandlerContext ctx) {
        LOG.debug("Starting session negotiation on channel {}", channel);
        try {
            startNegotiation();
        } catch (final Exception e) {
            LOG.warn("Unexpected negotiation failure", e);
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
            LOG.debug("Unexpected error while handling negotiation message {}", msg, e);
            negotiationFailed(e);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        LOG.info("Unexpected error during negotiation", cause);
        negotiationFailed(cause);
    }

    protected abstract void handleMessage(NetconfHelloMessage msg) throws Exception;
}
