/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import java.io.InputStream;
import java.util.Optional;
import javax.xml.transform.dom.DOMSource;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.MessageCounter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.copy.config.input.target.ConfigTarget;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.edit.config.input.EditContent;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.edit.config.input.target.config.target.Candidate;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class BaseRpcSchemalessTransformerTest {

    static {
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);
    }

    private static final String EXP_RPC = "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "   <edit-config>\n"
            + "       <target>\n"
            + "           <candidate/>\n"
            + "       </target>\n"
            + "       <config>\n"
            + "           <top xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "               <users xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:operation=\"replace\">\n"
            + "                   <user>\n"
            + "                       <name>fred</name>\n"
            + "                   </user>\n"
            + "               </users>\n"
            + "           </top>\n"
            + "       </config>\n"
            + "   </edit-config>\n"
            + "</rpc>\n";

    BaseRpcSchemalessTransformer transformer;

    @Before
    public void setUp() throws Exception {
        transformer = new BaseRpcSchemalessTransformer(new MessageCounter());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void toNotification() throws Exception {
        transformer.toNotification(new NetconfMessage(
                XmlUtil.readXmlToDocument(getClass().getResourceAsStream("/notification-payload.xml"))));
    }

    @Test
    public void toRpcRequest() throws Exception {
        final Document doc =
                XmlUtil.readXmlToDocument(getClass().getResourceAsStream("/schemaless/edit-config/container.xml"));
        final AnyXmlNode xml = Builders.anyXmlBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(
                        NetconfMessageTransformUtil.NETCONF_CONFIG_QNAME))
                .withValue(new DOMSource(doc.getDocumentElement()))
                .build();
        final ChoiceNode editContent = Builders.choiceBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(EditContent.QNAME))
                .withChild(xml)
                .build();
        final ChoiceNode candidate = Builders.choiceBuilder().withNodeIdentifier(
                new YangInstanceIdentifier.NodeIdentifier(ConfigTarget.QNAME))
                .withChild(Builders.leafBuilder().withNodeIdentifier(
                        new YangInstanceIdentifier.NodeIdentifier(Candidate.QNAME))
                    .withValue(Empty.getInstance()).build())
                .build();
        final DataContainerChild<?, ?> target = Builders.containerBuilder()
                .withNodeIdentifier(
                        new YangInstanceIdentifier.NodeIdentifier(NetconfMessageTransformUtil.NETCONF_TARGET_QNAME))
                .withChild(candidate)
                .build();
        final ContainerNode editConfig = Builders.containerBuilder()
                .withNodeIdentifier(
                    new YangInstanceIdentifier.NodeIdentifier(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME))
                .withChild(editContent)
                .withChild(target)
                .build();
        final NetconfMessage msg = transformer.toRpcRequest(
                SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), editConfig);
        final Diff diff = XMLUnit.compareXML(EXP_RPC, XmlUtil.toString(msg.getDocument()));
        Assert.assertTrue(diff.toString(), diff.similar());
    }

    @Test
    public void toRpcResult() throws Exception {
        final Document doc = XmlUtil.readXmlToDocument(
                "<rpc-reply message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>");
        final InputStream stream = getClass().getResourceAsStream("/schemaless/get-config/container.xml");
        final Element dataElement = XmlUtil.readXmlToElement(stream);
        final Element element = (Element) doc.importNode(dataElement, true);
        doc.getDocumentElement().appendChild(element);
        final NetconfMessage msg = new NetconfMessage(doc);
        final DOMRpcResult result = transformer.toRpcResult(msg,
                SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME));
        Assert.assertNotNull(result.getResult());
        final ContainerNode rpcReply = (ContainerNode) result.getResult();
        Assert.assertEquals(NetconfMessageTransformUtil.NETCONF_RPC_REPLY_QNAME, rpcReply.getNodeType());
        final Optional<?> dataOpt = rpcReply.getChild(
                new YangInstanceIdentifier.NodeIdentifier(NetconfMessageTransformUtil.NETCONF_DATA_QNAME));
        Assert.assertTrue(dataOpt.isPresent());
        final AnyXmlNode data = (AnyXmlNode) dataOpt.get();
        final Diff diff = XMLUnit.compareXML(dataElement.getOwnerDocument(), (Document) data.getValue().getNode());
        Assert.assertTrue(diff.toString(), diff.similar());
    }

}