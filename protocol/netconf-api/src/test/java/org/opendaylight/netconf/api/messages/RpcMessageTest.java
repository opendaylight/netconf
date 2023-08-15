/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class RpcMessageTest {
    private static RpcMessage RPC_MESSAGE;

    @BeforeAll
    public static void beforeAll() {
        final Document document = UntrustedXML.newDocumentBuilder().newDocument();

        final Element rootElement = document.createElement("test-root");
        document.appendChild(rootElement);

        RPC_MESSAGE = RpcMessage.wrapDocumentAsRpc(document, "1014");
    }

    @Test
    void testWrapRpc() {
        final Document resultDocument = RPC_MESSAGE.getDocument();

        final NodeList nodeList = resultDocument.getElementsByTagNameNS(NamespaceURN.BASE,
            XmlNetconfConstants.RPC_KEY);
        assertNotNull(nodeList);
        assertEquals(1, nodeList.getLength());

        final Element entireRpc = (Element) nodeList.item(0);
        assertEquals("1014", entireRpc.getAttribute(XmlNetconfConstants.MESSAGE_ID));
        assertNotNull(entireRpc.getElementsByTagName("test-root"));
    }

    @Test
    void testIsRpcMessage() {
        final Document rpcDocument = RPC_MESSAGE.getDocument();
        assertTrue(RpcMessage.isRpcMessage(rpcDocument));
    }

    @Test
    void testUnsafeOf() throws IOException, SAXException {
        final Document rpcDocument = RPC_MESSAGE.getDocument();
        final RpcMessage rpcMessage = RpcMessage.unsafeOf(rpcDocument);
        assertEquals("1014", rpcMessage.getMessageId());

        final Document invalidDocument = XmlUtil.readXmlToDocument("<foo/>");
        final RpcMessage invalidMessage = RpcMessage.unsafeOf(invalidDocument);
        assertEquals("", invalidMessage.getMessageId());
    }
}
