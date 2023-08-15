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

public final class RpcMessage extends NetconfMessage {

    private final String messageId;

    private RpcMessage(final Document document, final String messageId) {
        super(document);
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }

    /**
     * Create new RpcMessage with provided document as content and message ID.
     */
    public static RpcMessage wrapDocumentAsRpc(final Document document, final String messageId) {
        return new RpcMessage(wrapRpc(document, messageId), messageId);
    }

    /**
     * Create new RpcMessage with provided document. Only to be used if we know that the document represents a valid
     * RpcMessage.
     */
    public static RpcMessage unsafeOf(final Document document) {
        final String messageId = document.getDocumentElement().getAttribute(XmlNetconfConstants.MESSAGE_ID);
        return new RpcMessage(document, messageId);
    }

    private static Document wrapRpc(final Document rpcContent, final String messageId) {
        requireNonNull(rpcContent);

        final Element baseRpc = rpcContent.getDocumentElement();
        final Element entireRpc = rpcContent.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.RPC_KEY);
        entireRpc.appendChild(baseRpc);
        entireRpc.setAttribute(XmlNetconfConstants.MESSAGE_ID, messageId);

        rpcContent.appendChild(entireRpc);
        return rpcContent;
    }

    public static boolean isRpcMessage(final Document document) {
        final XmlElement element = XmlElement.fromDomElement(document.getDocumentElement());
        if (!element.getName().equals(XmlNetconfConstants.RPC_KEY)) {
            return false;
        }
        final String namespace = element.namespace();
        if (namespace == null || !namespace.equals(NamespaceURN.BASE)) {
            return false;
        }
        return !element.getAttribute(XmlNetconfConstants.MESSAGE_ID).isEmpty();
    }
}
