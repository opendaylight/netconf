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

public final class RpcMessage extends NetconfMessage {

    public RpcMessage(final Document document, final int messageId) {
        super(wrapRpc(document, messageId));
    }

    private static Document wrapRpc(final Document rpcContent, final int messageId) {
        requireNonNull(rpcContent);

        final Element baseRpc = rpcContent.getDocumentElement();
        final Element entireRpc = rpcContent.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.RPC_KEY);
        entireRpc.appendChild(baseRpc);
        entireRpc.setAttribute("message-id", String.valueOf(messageId));

        rpcContent.appendChild(entireRpc);
        return rpcContent;
    }
}
