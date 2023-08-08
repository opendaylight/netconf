/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static java.util.Objects.requireNonNull;

import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class RpcReplyMessage extends NetconfMessage {
    private final String messageId;

    private RpcReplyMessage(final Document document, final String messageId) {
        super(document);
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }

    /**
     * Create new RpcReplyMessage with provided message ID.
     */
    public static RpcReplyMessage wrapDocumentAsRpcReply(final Document document, final String messageId) {
        return new RpcReplyMessage(wrapRpcReply(document, messageId), messageId);
    }

    private static Document wrapRpcReply(final Document rpcContent, final String messageId) {
        requireNonNull(rpcContent);

        final Element baseRpc = rpcContent.getDocumentElement();
        final Element entireRpc = rpcContent.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.RPC_REPLY_KEY);
        entireRpc.appendChild(baseRpc);
        entireRpc.setAttribute(XmlNetconfConstants.MESSAGE_ID, messageId);

        rpcContent.appendChild(entireRpc);
        return rpcContent;
    }
}
