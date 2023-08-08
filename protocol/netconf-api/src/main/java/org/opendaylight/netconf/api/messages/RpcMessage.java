/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.w3c.dom.Document;

public final class RpcMessage extends NetconfMessage {
    private final @NonNull String messageId;

    private RpcMessage(final Document document, final String messageId) {
        super(document);
        final var root = document.getDocumentElement();
        final var rootNs = root.getNamespaceURI();
        checkArgument(NamespaceURN.BASE.equals(rootNs), "Unexpected element namespace %s", rootNs);
        final var rootName = root.getLocalName();
        checkArgument(XmlNetconfConstants.RPC_KEY.equals(rootName), "Unexpected element name %s", rootName);
        this.messageId = requireNonNull(messageId);
    }

    public @NonNull String messageId() {
        return messageId;
    }

    /**
     * Create new RpcMessage with provided message ID.
     */
    @VisibleForTesting
    static @NonNull RpcMessage wrapDocumentAsRpc(final Document document, final String messageId) {
        return new RpcMessage(wrapRpc(document, messageId), messageId);
    }

    private static Document wrapRpc(final Document rpcContent, final String messageId) {
        requireNonNull(rpcContent);

        final var entireRpc = rpcContent.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.RPC_KEY);
        entireRpc.appendChild(rpcContent.getDocumentElement());
        entireRpc.setAttribute(XmlNetconfConstants.MESSAGE_ID, messageId);

        rpcContent.appendChild(entireRpc);
        return rpcContent;
    }
}
