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
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class RpcReplyMessage extends NetconfMessage {

    private RpcReplyMessage(final Document document) {
        super(document);
    }

    /**
     * Create new RpcReplyMessage with provided message ID.
     */
    public static RpcReplyMessage wrapDocumentAsRpcReply(final Document document, final String messageId) {
        return new RpcReplyMessage(wrapRpcReply(document, messageId));
    }

    public static RpcReplyMessage unsafeOf(final Document document) {
        return new RpcReplyMessage(document);
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

    public static boolean isRpcReplyMessage(final Document document) {
        final XmlElement element = XmlElement.fromDomElement(document.getDocumentElement());
        if (!element.getName().equals(XmlNetconfConstants.RPC_REPLY_KEY)) {
            return false;
        }
        final String namespace = element.namespace();
        if (namespace == null || !namespace.equals(NamespaceURN.BASE)) {
            return false;
        }
        return !element.getAttribute(XmlNetconfConstants.MESSAGE_ID).isEmpty();
    }
}
