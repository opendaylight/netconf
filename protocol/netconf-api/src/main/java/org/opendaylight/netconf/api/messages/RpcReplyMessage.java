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
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Document;

/**
 * A NETCONF RPC reply message, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc6241#section-4.2">RFC6241, section 4.2</a>. Its document element is
 * guaranteed to be an {@code <rpc-reply/>} in the {@value NamespaceURN#BASE} namespace.
 */
public final class RpcReplyMessage extends NetconfMessage {
    public static final @NonNull String ELEMENT_NAME = "rpc-reply";

    RpcReplyMessage(final Document document) {
        super(document);
    }

    /**
     * Return an {@link RpcReplyMessage} backed by specified {@link Document}.
     *
     * @param document Backing document
     * @return An {@link RpcReplyMessage}
     * @throws DocumentedException if the document's structure does not form a valid {@code rpc-reply} message
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public static @NonNull RpcReplyMessage of(final Document document) throws DocumentedException {
        final var root = document.getDocumentElement();
        final var rootName = root.getLocalName();
        if (!ELEMENT_NAME.equals(rootName)) {
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

        return new RpcReplyMessage(document);
    }

    /**
     * Return an {@link RpcReplyMessage} representation.
     *
     * @param ex DocumentedException specifying the error
     * @return An {@link RpcReplyMessage}
     * @throws NullPointerException if {@code ex} is {@code null}
     */
    public static @NonNull RpcReplyMessage of(final DocumentedException ex) {
        return new RpcReplyMessage(ex.toXMLDocument());
    }

    public static @NonNull RpcReplyMessage ofOperation(final String messageId, final Document document) {
        final var rpcElem = document.createElementNS(NamespaceURN.BASE, ELEMENT_NAME);
        rpcElem.appendChild(document.getDocumentElement());
        rpcElem.setAttribute(XmlNetconfConstants.MESSAGE_ID, requireNonNull(messageId));
        document.appendChild(rpcElem);
        return new RpcReplyMessage(document);
    }

    public @Nullable String messageId() {
        final var attr = getDocument().getDocumentElement().getAttributeNode(XmlNetconfConstants.MESSAGE_ID);
        return attr == null ? null : attr.getValue();
    }
}
