/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Document;

/**
 * A NETCONF RPC request message, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc6241#section-4.1">RFC6241, section 4.1</a>. Its document element is
 * guaranteed to be an {@code <rpc/>} in the {@value NamespaceURN#BASE} namespace.
 */
public final class RpcMessage extends NetconfMessage {
    private RpcMessage(final Document document) {
        super(document);
    }

    /**
     * Return an {@link RpcMessage} backed by specified {@link Document}.
     *
     * @param document Backing document
     * @return An {@link RpcMessage}
     * @throws DocumentedException if the document's structure does not form a valid {@code rpc} message
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public static @NonNull RpcMessage of(final Document document) throws DocumentedException {
        final var root = document.getDocumentElement();
        final var rootName = root.getLocalName();
        if (!XmlNetconfConstants.RPC_KEY.equals(rootName)) {
            throw new DocumentedException("Unexpected element name " + rootName, ErrorType.PROTOCOL,
                ErrorTag.UNKNOWN_ELEMENT, ErrorSeverity.ERROR, ImmutableMap.of("bad-element", rootName));
        }
        final var rootNs = root.getNamespaceURI();
        if (!NamespaceURN.BASE.equals(rootNs)) {
            throw new DocumentedException("Unexpected element namespace " + rootNs, ErrorType.PROTOCOL,
                ErrorTag.UNKNOWN_NAMESPACE, ErrorSeverity.ERROR, ImmutableMap.of(
                    "bad-element", rootName,
                    "bad-namespace", rootNs));
        }

        final var messageIdAttr = root.getAttributeNode(XmlNetconfConstants.MESSAGE_ID);
        if (messageIdAttr == null) {
            throw new DocumentedException("Missing message-id attribute", ErrorType.RPC, ErrorTag.MISSING_ATTRIBUTE,
                ErrorSeverity.ERROR, ImmutableMap.of(
                    "bad-attribute", XmlNetconfConstants.MESSAGE_ID,
                    "bad-element", XmlNetconfConstants.RPC_KEY));
        }
        if (!messageIdAttr.getSpecified()) {
            throw new IllegalArgumentException("Document element's message-id attribute is not specified");
        }
        return new RpcMessage(document);
    }

    /**
     * Return an {@link RpcMessage} wrapping an RPC operation supplied as {@link Document}. The supplied document is
     * modified to have its document element replaced with a {@code <rpc/>} element which contains it.
     *
     * @param document Backing operation document
     * @return An {@link RpcMessage}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static @NonNull RpcMessage ofOperation(final String messageId, final Document document) {
        final var rpcElem = document.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.RPC_KEY);
        rpcElem.appendChild(document.getDocumentElement());
        rpcElem.setAttribute(XmlNetconfConstants.MESSAGE_ID, requireNonNull(messageId));
        document.appendChild(rpcElem);
        return new RpcMessage(document);
    }

    public @NonNull String messageId() {
        return getDocument().getDocumentElement().getAttribute(XmlNetconfConstants.MESSAGE_ID);
    }

    public static boolean isRpcMessage(final Document document) {
        final var root = document.getDocumentElement();
        return NamespaceURN.BASE.equals(root.getNamespaceURI())
            && XmlNetconfConstants.RPC_KEY.equals(root.getLocalName());
    }
}
