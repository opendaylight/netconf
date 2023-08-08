/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.yangtools.util.xml.UntrustedXML;

public class RpcMessageTest {
    @Test
    void testWrapRpc() {
        final var document = UntrustedXML.newDocumentBuilder().newDocument();
        final var rootElement = document.createElement("test-root");
        document.appendChild(rootElement);

        final var msgRoot = RpcMessage.wrapDocumentAsRpc(document, "1014").getDocument().getDocumentElement();
        assertEquals(NamespaceURN.BASE, msgRoot.getNamespaceURI());
        assertEquals(XmlNetconfConstants.RPC_KEY, msgRoot.getLocalName());
        assertEquals("1014", msgRoot.getAttribute(XmlNetconfConstants.MESSAGE_ID));

        final var rootList = msgRoot.getChildNodes();
        assertEquals(1, rootList.getLength());
        assertSame(rootElement, rootList.item(0));
    }
}
