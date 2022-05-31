/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.client;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.Timer;
import io.netty.util.concurrent.Promise;
import java.util.Set;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import org.opendaylight.netconf.api.NetconfClientSessionPreferences;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;
import org.opendaylight.netconf.nettyutil.AbstractNetconfSessionNegotiator;
import org.opendaylight.netconf.nettyutil.handler.exi.NetconfStartExiMessage;
import org.opendaylight.netconf.util.messages.NetconfMessageUtil;
import org.opendaylight.netconf.util.xml.XMLNetconfUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetconfClientSessionNegotiator extends
        AbstractNetconfSessionNegotiator<NetconfClientSessionPreferences, NetconfClientSession,
                NetconfClientSessionListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfClientSessionNegotiator.class);

    private static final XPathExpression SESSION_ID_X_PATH = XMLNetconfUtil
            .compileXPath("/netconf:hello/netconf:session-id");

    private static final XPathExpression SESSION_ID_X_PATH_NO_NAMESPACE = XMLNetconfUtil
            .compileXPath("/hello/session-id");

    private static final String EXI_1_0_CAPABILITY_MARKER = "exi:1.0";

    private static final Interner<Set<String>> INTERNER = Interners.newWeakInterner();

    protected NetconfClientSessionNegotiator(final NetconfClientSessionPreferences sessionPreferences,
                                             final Promise<NetconfClientSession> promise,
                                             final Channel channel,
                                             final Timer timer,
                                             final NetconfClientSessionListener sessionListener,
                                             final long connectionTimeoutMillis) {
        super(sessionPreferences, promise, channel, timer, sessionListener, connectionTimeoutMillis);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    @SuppressFBWarnings("BC_UNCONFIRMED_CAST")
    protected void handleMessage(final NetconfHelloMessage netconfMessage) throws NetconfDocumentedException {
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
        final NetconfMessage startExiMessage = sessionPreferences.getStartExiMessage();
        if (shouldUseExi(netconfMessage) && startExiMessage instanceof NetconfStartExiMessage) {
            LOG.debug("Netconf session {} should use exi.", session);
            tryToInitiateExi(session, (NetconfStartExiMessage) startExiMessage);
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
    void tryToInitiateExi(final NetconfClientSession session, final NetconfStartExiMessage startExiMessage) {
        channel.pipeline().addAfter(AbstractChannelInitializer.NETCONF_MESSAGE_DECODER,
                ExiConfirmationInboundHandler.EXI_CONFIRMED_HANDLER,
                new ExiConfirmationInboundHandler(session, startExiMessage));

        session.sendMessage(startExiMessage).addListener(channelFuture -> {
            if (!channelFuture.isSuccess()) {
                LOG.warn("Failed to send start-exi message {} on session {}", startExiMessage, session,
                        channelFuture.cause());
                channel.pipeline().remove(ExiConfirmationInboundHandler.EXI_CONFIRMED_HANDLER);
            } else {
                LOG.trace("Start-exi message {} sent to socket on session {}", startExiMessage, session);
            }
        });
    }

    @SuppressFBWarnings("BC_UNCONFIRMED_CAST")
    private boolean shouldUseExi(final NetconfHelloMessage helloMsg) {
        return containsExi10Capability(helloMsg.getDocument())
                && containsExi10Capability(sessionPreferences.getHelloMessage().getDocument());
    }

    private static boolean containsExi10Capability(final Document doc) {
        final NodeList nList = doc.getElementsByTagName(XmlNetconfConstants.CAPABILITY);
        for (int i = 0; i < nList.getLength(); i++) {
            if (nList.item(i).getTextContent().contains(EXI_1_0_CAPABILITY_MARKER)) {
                return true;
            }
        }
        return false;
    }

    private static long extractSessionId(final Document doc) {
        String textContent = getSessionIdWithXPath(doc, SESSION_ID_X_PATH);
        if (Strings.isNullOrEmpty(textContent)) {
            textContent = getSessionIdWithXPath(doc, SESSION_ID_X_PATH_NO_NAMESPACE);
            if (Strings.isNullOrEmpty(textContent)) {
                throw new IllegalStateException("Session id not received from server, hello message: " + XmlUtil
                        .toString(doc));
            }
        }

        return Long.parseLong(textContent);
    }

    private static String getSessionIdWithXPath(final Document doc, final XPathExpression sessionIdXPath) {
        final Node sessionIdNode = (Node) XmlUtil.evaluateXPath(sessionIdXPath, doc, XPathConstants.NODE);
        return sessionIdNode != null ? sessionIdNode.getTextContent() : null;
    }

    @Override
    protected NetconfClientSession getSession(final NetconfClientSessionListener sessionListener, final Channel channel,
                                              final NetconfHelloMessage message) {
        final long sessionId = extractSessionId(message.getDocument());

        // Copy here is important: it disconnects the strings from the document
        Set<String> capabilities = ImmutableSet.copyOf(NetconfMessageUtil.extractCapabilitiesFromHello(message
                .getDocument()));

        capabilities = INTERNER.intern(capabilities);

        return new NetconfClientSession(sessionListener, channel, sessionId, capabilities);
    }

    /**
     * Handler to process response for start-exi message.
     */
    private final class ExiConfirmationInboundHandler extends ChannelInboundHandlerAdapter {
        private static final String EXI_CONFIRMED_HANDLER = "exiConfirmedHandler";

        private final NetconfClientSession session;
        private final NetconfStartExiMessage startExiMessage;

        ExiConfirmationInboundHandler(final NetconfClientSession session,
                                      final NetconfStartExiMessage startExiMessage) {
            this.session = session;
            this.startExiMessage = startExiMessage;
        }

        @SuppressWarnings("checkstyle:IllegalCatch")
        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
            ctx.pipeline().remove(ExiConfirmationInboundHandler.EXI_CONFIRMED_HANDLER);

            NetconfMessage netconfMessage = (NetconfMessage) msg;

            // Ok response to start-exi, try to add exi handlers
            if (NetconfMessageUtil.isOKMessage(netconfMessage)) {
                LOG.trace("Positive response on start-exi call received on session {}", session);
                try {
                    session.startExiCommunication(startExiMessage);
                } catch (RuntimeException e) {
                    // Unable to add exi, continue without exi
                    LOG.warn("Unable to start exi communication, Communication will continue without exi on session "
                            + "{}", session, e);
                }

                // Error response
            } else if (NetconfMessageUtil.isErrorMessage(netconfMessage)) {
                LOG.warn(
                        "Error response to start-exi message {}, Communication will continue without exi on session {}",
                        netconfMessage, session);

                // Unexpected response to start-exi, throwing message away, continue without exi
            } else {
                LOG.warn("Unexpected response to start-exi message, should be ok, was {}, "
                        + "Communication will continue without exi "
                        + "and response message will be thrown away on session {}",
                        netconfMessage, session);
            }

            negotiationSuccessful(session);
        }
    }

}
