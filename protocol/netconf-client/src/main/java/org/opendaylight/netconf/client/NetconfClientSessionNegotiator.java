/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Promise;
import java.util.Set;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import org.checkerframework.checker.index.qual.NonNegative;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.messages.RpcMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.nettyutil.NetconfSessionNegotiator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

// Non-final for mocking
class NetconfClientSessionNegotiator
        extends NetconfSessionNegotiator<NetconfClientSession, NetconfClientSessionListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfClientSessionNegotiator.class);

    private static final XPathExpression SESSION_ID_X_PATH = XMLNetconfUtil
            .compileXPath("/netconf:hello/netconf:session-id");

    private static final XPathExpression SESSION_ID_X_PATH_NO_NAMESPACE = XMLNetconfUtil
            .compileXPath("/hello/session-id");

    private static final String EXI_1_0_CAPABILITY_MARKER = "exi:1.0";

    private static final Interner<Set<String>> INTERNER = Interners.newWeakInterner();

    private final RpcMessage startExi;

    NetconfClientSessionNegotiator(final HelloMessage hello, final RpcMessage startExi,
            final Promise<NetconfClientSession> promise, final Channel channel, final NetconfTimer timer,
            final NetconfClientSessionListener sessionListener, final long connectionTimeoutMillis,
            final @NonNegative int maximumIncomingChunkSize) {
        super(hello, promise, channel, timer, sessionListener, connectionTimeoutMillis, maximumIncomingChunkSize);
        this.startExi = startExi;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void handleMessage(final HelloMessage netconfMessage) throws NetconfDocumentedException {
        if (!ifNegotiatedAlready()) {
            LOG.debug("Server hello message received, starting negotiation on channel {}", channel);
            try {
                startNegotiation();
            } catch (final Exception e) {
                LOG.warn("Unexpected negotiation failure on channel {}", channel, e);
                negotiationFailed(e);
                return;
            }
        }
        final NetconfClientSession session = getSessionForHelloMessage(netconfMessage);
        replaceHelloMessageInboundHandler(session);

        // If exi should be used, try to initiate exi communication
        // Call negotiationSuccessFul after exi negotiation is finished successfully or not
        if (startExi != null && shouldUseExi(netconfMessage)) {
            LOG.debug("Netconf session {} should use exi.", session);
            tryToInitiateExi(session, startExi);
        } else {
            // Exi is not supported, release session immediately
            LOG.debug("Netconf session {} isn't capable of using exi.", session);
            negotiationSuccessful(session);
        }
    }

    /**
     * Initiates exi communication by sending start-exi message and waiting for positive/negative response.
     *
     * @param startExiMessage Exi message for initilization of exi communication.
     */
    void tryToInitiateExi(final NetconfClientSession session, final RpcMessage startExiMessage) {
        channel.pipeline().addAfter(MessageDecoder.HANDLER_NAME, ExiConfirmationInboundHandler.EXI_CONFIRMED_HANDLER,
                new ExiConfirmationInboundHandler(session, startExiMessage));

        session.sendMessage(startExiMessage).addListener(channelFuture -> {
            final var cause = channelFuture.cause();
            if (cause != null) {
                LOG.warn("Failed to send start-exi message {} on session {}", startExiMessage, session, cause);
                channel.pipeline().remove(ExiConfirmationInboundHandler.EXI_CONFIRMED_HANDLER);
            } else {
                LOG.trace("Start-exi message {} sent to socket on session {}", startExiMessage, session);
            }
        });
    }

    private boolean shouldUseExi(final HelloMessage helloMsg) {
        return containsExi10Capability(helloMsg.getDocument()) && containsExi10Capability(localHello().getDocument());
    }

    private static boolean containsExi10Capability(final Document doc) {
        final var nodeList = doc.getElementsByTagName(XmlNetconfConstants.CAPABILITY);
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (nodeList.item(i).getTextContent().contains(EXI_1_0_CAPABILITY_MARKER)) {
                return true;
            }
        }
        return false;
    }

    private static @NonNull SessionIdType extractSessionId(final Document doc) {
        String textContent = getSessionIdWithXPath(doc, SESSION_ID_X_PATH);
        if (Strings.isNullOrEmpty(textContent)) {
            textContent = getSessionIdWithXPath(doc, SESSION_ID_X_PATH_NO_NAMESPACE);
            if (Strings.isNullOrEmpty(textContent)) {
                throw new IllegalStateException("Session id not received from server, hello message: " + XmlUtil
                        .toString(doc));
            }
        }
        return new SessionIdType(Uint32.valueOf(textContent));
    }

    private static String getSessionIdWithXPath(final Document doc, final XPathExpression sessionIdXPath) {
        final var sessionIdNode = evaluateXPath(sessionIdXPath, doc);
        return sessionIdNode != null ? sessionIdNode.getTextContent() : null;
    }

    @Override
    protected NetconfClientSession getSession(final NetconfClientSessionListener sessionListener, final Channel channel,
                                              final HelloMessage message) {
        final var sessionId = extractSessionId(message.getDocument());

        // Copy here is important: it disconnects the strings from the document
        final var capabilities = INTERNER.intern(ImmutableSet.copyOf(
            NetconfMessageUtil.extractCapabilitiesFromHello(message .getDocument())));

        return new NetconfClientSession(sessionListener, channel, sessionId, capabilities);
    }

    @VisibleForTesting
    static Node evaluateXPath(final XPathExpression expr, final Object rootNode) {
        try {
            return (Node) expr.evaluate(rootNode, XPathConstants.NODE);
        } catch (final XPathExpressionException e) {
            throw new IllegalStateException("Error while evaluating xpath expression " + expr, e);
        }
    }

    /**
     * Handler to process response for start-exi message.
     */
    private final class ExiConfirmationInboundHandler extends ChannelInboundHandlerAdapter {
        private static final String EXI_CONFIRMED_HANDLER = "exiConfirmedHandler";

        private final NetconfClientSession session;
        private final RpcMessage startExiMessage;

        ExiConfirmationInboundHandler(final NetconfClientSession session,
                                      final RpcMessage startExiMessage) {
            this.session = session;
            this.startExiMessage = startExiMessage;
        }

        @SuppressWarnings("checkstyle:IllegalCatch")
        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
            ctx.pipeline().remove(ExiConfirmationInboundHandler.EXI_CONFIRMED_HANDLER);

            NetconfMessage netconfMessage = (NetconfMessage) msg;

            // Ok response to start-exi, try to add exi handlers
            if (NetconfMessageUtil.isOKMessage(netconfMessage)) {
                LOG.trace("Positive response on start-exi call received on session {}", session);
                try {
                    session.startExiCommunication(startExiMessage);
                } catch (RuntimeException e) {
                    // Unable to add exi, continue without exi
                    LOG.warn("Unable to start exi communication, Communication will continue without exi on session {}",
                        session, e);
                }

                // Error response
            } else if (NetconfMessageUtil.isErrorMessage(netconfMessage)) {
                LOG.warn(
                        "Error response to start-exi message {}, Communication will continue without exi on session {}",
                        netconfMessage, session);

                // Unexpected response to start-exi, throwing message away, continue without exi
            } else {
                LOG.warn("""
                    Unexpected response to start-exi message, should be ok, was {}. Communication will continue \
                    without EXI and response message will be thrown away on session {}""", netconfMessage, session);
            }

            negotiationSuccessful(session);
        }
    }

}
