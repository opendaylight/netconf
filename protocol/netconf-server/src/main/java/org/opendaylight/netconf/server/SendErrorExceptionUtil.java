/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static com.google.common.base.Preconditions.checkState;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import javax.xml.XMLConstants;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.NetconfSession;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.messages.RpcMessage;
import org.opendaylight.netconf.api.messages.RpcReplyMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;

final class SendErrorExceptionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(SendErrorExceptionUtil.class);

    private SendErrorExceptionUtil() {
        // Hidden on purpose
    }

    static void sendErrorMessage(final NetconfSession session, final DocumentedException sendErrorException) {
        LOG.trace("Sending error", sendErrorException);
        final Document errorDocument = createDocument(sendErrorException);
        ChannelFuture channelFuture = session.sendMessage(new NetconfMessage(errorDocument));
        channelFuture.addListener(new SendErrorVerifyingListener(sendErrorException));
    }

    static void sendErrorMessage(final Channel channel, final DocumentedException sendErrorException) {
        LOG.trace("Sending error", sendErrorException);
        final Document errorDocument = createDocument(sendErrorException);
        ChannelFuture channelFuture = channel.writeAndFlush(new NetconfMessage(errorDocument));
        channelFuture.addListener(new SendErrorVerifyingListener(sendErrorException));
    }

    static void sendErrorMessage(final NetconfSession session, final DocumentedException sendErrorException,
            final NetconfMessage incommingMessage) {
        final Document errorDocument = createDocument(sendErrorException);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Sending error {}", XmlUtil.toString(errorDocument));
        }

        tryToCopyAttributes(incommingMessage.getDocument(), errorDocument, sendErrorException);
        ChannelFuture channelFuture = session.sendMessage(new NetconfMessage(errorDocument));
        channelFuture.addListener(new SendErrorVerifyingListener(sendErrorException));
    }

    // FIXME: this should be handled through RpcMessage.toReply(DocumentedException)
    @SuppressWarnings("checkstyle:IllegalCatch")
    private static void tryToCopyAttributes(final Document incommingDocument, final Document errorDocument,
            final DocumentedException sendErrorException) {
        try {
            final var incommingRpc = incommingDocument.getDocumentElement();
            checkState(RpcMessage.ELEMENT_NAME.equals(incommingRpc.getLocalName())
                && NamespaceURN.BASE.equals(incommingRpc.getNamespaceURI()), "Missing %s element",
                RpcMessage.ELEMENT_NAME);

            final var rpcReply = errorDocument.getDocumentElement();
            checkState(rpcReply.getTagName().equals(RpcReplyMessage.ELEMENT_NAME), "Missing %s element",
                RpcReplyMessage.ELEMENT_NAME);

            final var incomingAttributes = incommingRpc.getAttributes();
            for (int i = 0; i < incomingAttributes.getLength(); i++) {
                final var attr = (Attr) incomingAttributes.item(i);
                // skip namespace
                if (attr.getNodeName().equals(XMLConstants.XMLNS_ATTRIBUTE)) {
                    continue;
                }
                rpcReply.setAttributeNode((Attr) errorDocument.importNode(attr, true));
            }
        } catch (final Exception e) {
            LOG.warn("Unable to copy incomming attributes to {}, returned rpc-error might be invalid for client",
                    sendErrorException, e);
        }
    }

    private static Document createDocument(final DocumentedException sendErrorException) {
        return sendErrorException.toXMLDocument();
    }

    /**
     * Checks if netconf error was sent successfully.
     */
    private static final class SendErrorVerifyingListener implements ChannelFutureListener {
        private final DocumentedException sendErrorException;

        SendErrorVerifyingListener(final DocumentedException sendErrorException) {
            this.sendErrorException = sendErrorException;
        }

        @Override
        public void operationComplete(final ChannelFuture channelFuture) {
            checkState(channelFuture.isSuccess(), "Unable to send exception %s", sendErrorException,
                channelFuture.cause());
        }
    }
}
