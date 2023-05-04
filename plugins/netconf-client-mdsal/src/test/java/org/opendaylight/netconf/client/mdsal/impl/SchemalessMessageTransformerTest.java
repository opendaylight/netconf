/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.xml.transform.dom.DOMSource;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class SchemalessMessageTransformerTest {

    static {
        XMLUnit.setIgnoreWhitespace(true);
    }

    private static final String EXP_REQUEST =
            "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                    + "<test-rpc xmlns=\"test-ns\">\n"
                    + "<input>aaa</input>\n"
                    + "</test-rpc>\n"
                    + "</rpc>";
    private static final String EXP_REPLY =
            "<rpc-reply message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                    + "<output xmlns=\"test-ns\">aaa</output>\n"
                    + "</rpc-reply>";
    private static final String OK_REPLY = "<rpc-reply message-id=\"101\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<ok/>\n"
            + "</rpc-reply>\n";

    private static final QName TEST_RPC = QName.create("test-ns", "2016-10-13", "test-rpc");

    private SchemalessMessageTransformer transformer;

    @Before
    public void setUp() throws Exception {
        transformer = new SchemalessMessageTransformer(new MessageCounter());
    }

    @Test
    public void toNotification() throws Exception {
        final Document payload = XmlUtil.readXmlToDocument(getClass().getResourceAsStream("/notification-payload.xml"));
        final NetconfMessage netconfMessage = new NetconfMessage(payload);
        final DOMNotification domNotification = transformer.toNotification(netconfMessage);
        assertEquals(domNotification.getType().lastNodeIdentifier(),
                SchemalessMessageTransformer.SCHEMALESS_NOTIFICATION_PAYLOAD.getNodeType());
        final QName qName =
                QName.create("org:opendaylight:notification:test:ns:yang:user-notification", "user-visited-page");
        final DOMSourceAnyxmlNode dataContainerChild = (DOMSourceAnyxmlNode) domNotification.getBody()
                .getChildByArg(new NodeIdentifier(qName));
        final Diff diff = XMLUnit.compareXML(payload, dataContainerChild.body().getNode().getOwnerDocument());
        assertTrue(diff.toString(), diff.similar());

    }

    @Test
    public void toRpcRequest() throws Exception {
        final Node src = XmlUtil.readXmlToDocument("<test-rpc xmlns=\"test-ns\"><input>aaa</input></test-rpc>");
        final NetconfMessage netconfMessage = transformer.toRpcRequest(TEST_RPC, new DOMSource(src));
        final Diff diff = XMLUnit.compareXML(XmlUtil.readXmlToDocument(EXP_REQUEST), netconfMessage.getDocument());
        assertTrue(diff.toString(), diff.similar());
    }

    @Test
    public void toRpcResult() throws Exception {
        final Document doc = XmlUtil.readXmlToDocument(EXP_REPLY);
        final NetconfMessage netconfMessage = new NetconfMessage(doc);
        final DOMSource result = transformer.toRpcResult(RpcResultBuilder.success(netconfMessage).build(), TEST_RPC);
        final Document domSourceDoc = (Document) result.getNode();
        final Diff diff = XMLUnit.compareXML(XmlUtil.readXmlToDocument(EXP_REPLY), domSourceDoc);
        assertTrue(diff.toString(), diff.similar());
    }

    @Test
    public void toEmptyRpcResult() throws Exception {
        final Document doc = XmlUtil.readXmlToDocument(OK_REPLY);
        final DOMSource result = transformer.toRpcResult(RpcResultBuilder.success(new NetconfMessage(doc)).build(),
            NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME);
        assertNull(result);
    }
}
