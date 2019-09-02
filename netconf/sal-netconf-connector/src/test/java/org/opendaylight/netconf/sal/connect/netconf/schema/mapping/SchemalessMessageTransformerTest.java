/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import javax.xml.transform.dom.DOMSource;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.MessageCounter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
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

    private SchemalessMessageTransformer transformer;
    private static final QName TEST_RPC = QName.create("test-ns", "2016-10-13", "test-rpc");
    private static final SchemaPath SCHEMA_PATH = SchemaPath.create(true, TEST_RPC);

    @Before
    public void setUp() throws Exception {
        transformer = new SchemalessMessageTransformer(new MessageCounter());
    }

    @Test
    public void toNotification() throws Exception {
        final Document payload = XmlUtil.readXmlToDocument(getClass().getResourceAsStream("/notification-payload.xml"));
        final NetconfMessage netconfMessage = new NetconfMessage(payload);
        final DOMNotification domNotification = transformer.toNotification(netconfMessage);
        Assert.assertEquals(domNotification.getType().getLastComponent(),
                SchemalessMessageTransformer.SCHEMALESS_NOTIFICATION_PAYLOAD.getNodeType());
        final QName qName =
                QName.create("org:opendaylight:notification:test:ns:yang:user-notification", "user-visited-page");
        final DOMSourceAnyxmlNode dataContainerChild = (DOMSourceAnyxmlNode) domNotification.getBody()
                .getChild(new YangInstanceIdentifier.NodeIdentifier(qName)).get();
        final Diff diff = XMLUnit.compareXML(payload, dataContainerChild.getValue().getNode().getOwnerDocument());
        Assert.assertTrue(diff.toString(), diff.similar());

    }

    @Test
    public void toRpcRequest() throws Exception {
        final Node src = XmlUtil.readXmlToDocument("<test-rpc xmlns=\"test-ns\"><input>aaa</input></test-rpc>");
        final DOMSourceAnyxmlNode input = Builders.anyXmlBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(TEST_RPC))
                .withValue(new DOMSource(src))
                .build();
        final NetconfMessage netconfMessage = transformer.toRpcRequest(SCHEMA_PATH, input);
        final Diff diff = XMLUnit.compareXML(XmlUtil.readXmlToDocument(EXP_REQUEST), netconfMessage.getDocument());
        Assert.assertTrue(diff.toString(), diff.similar());
    }

    @Test
    public void toRpcResult() throws Exception {
        final Document doc = XmlUtil.readXmlToDocument(EXP_REPLY);
        final NetconfMessage netconfMessage = new NetconfMessage(doc);
        final DOMRpcResult result = transformer.toRpcResult(netconfMessage, SCHEMA_PATH);
        final DOMSource value = (DOMSource) result.getResult().getValue();
        Assert.assertNotNull(result.getResult());
        final Document domSourceDoc = (Document) value.getNode();
        final Diff diff = XMLUnit.compareXML(XmlUtil.readXmlToDocument(EXP_REPLY), domSourceDoc);
        Assert.assertTrue(diff.toString(), diff.similar());
    }

    @Test
    public void toEmptyRpcResult() throws Exception {
        final Document doc = XmlUtil.readXmlToDocument(OK_REPLY);
        final DOMRpcResult result = transformer.toRpcResult(
                new NetconfMessage(doc), SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME));
        Assert.assertNull(result.getResult());
    }

}
