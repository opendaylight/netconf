/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import javax.xml.transform.dom.DOMSource;
import org.junit.Test;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.AbstractBaseSchemasTest;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.copy.config.input.target.ConfigTarget;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.edit.config.input.EditContent;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.edit.config.input.target.config.target.Candidate;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.w3c.dom.Element;
import org.xmlunit.builder.DiffBuilder;

public class BaseRpcSchemalessTransformerTest extends AbstractBaseSchemasTest {
    private final BaseRpcSchemalessTransformer transformer =
        new BaseRpcSchemalessTransformer(BASE_SCHEMAS, new MessageCounter());

    @Test
    public void toRpcRequest() throws Exception {
        final var msg = transformer.toRpcRequest(EditConfig.QNAME,
            Builders.containerBuilder()
                .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_NODEID)
                .withChild(Builders.choiceBuilder()
                    .withNodeIdentifier(new NodeIdentifier(EditContent.QNAME))
                    .withChild(Builders.anyXmlBuilder()
                        .withNodeIdentifier(new NodeIdentifier(NetconfMessageTransformUtil.NETCONF_CONFIG_QNAME))
                        .withValue(new DOMSource(XmlUtil.readXmlToDocument(
                            BaseRpcSchemalessTransformerTest.class.getResourceAsStream(
                                "/schemaless/edit-config/container.xml"))
                            .getDocumentElement()))
                        .build())
                    .build())
                .withChild(Builders.containerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(NetconfMessageTransformUtil.NETCONF_TARGET_QNAME))
                    .withChild(Builders.choiceBuilder()
                        .withNodeIdentifier(new NodeIdentifier(ConfigTarget.QNAME))
                        .withChild(ImmutableNodes.leafNode(Candidate.QNAME, Empty.value()))
                        .build())
                    .build())
                .build());

        final var diff = DiffBuilder.compare("""
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
               <edit-config>
                   <target>
                       <candidate/>
                   </target>
                   <config>
                       <top xmlns="http://example.com/schema/1.2/config">
                           <users xmlns:ns0="urn:ietf:params:xml:ns:netconf:base:1.0" ns0:operation="replace">
                               <user>
                                   <name>fred</name>
                               </user>
                           </users>
                       </top>
                   </config>
               </edit-config>
            </rpc>""")
            .withTest(msg.getDocument())
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void toRpcResult() throws Exception {
        final var doc = XmlUtil.readXmlToDocument(
                "<rpc-reply message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>");
        final var dataElement = XmlUtil.readXmlToElement(
            BaseRpcSchemalessTransformerTest.class.getResourceAsStream("/schemaless/get-config/container.xml"));
        final var element = (Element) doc.importNode(dataElement, true);
        doc.getDocumentElement().appendChild(element);
        final var msg = new NetconfMessage(doc);
        final var result = transformer.toRpcResult(RpcResultBuilder.success(msg).build(), GetConfig.QNAME);
        assertNotNull(result.value());
        final var rpcReply = result.value();
        assertEquals(NetconfMessageTransformUtil.NETCONF_RPC_REPLY_QNAME, rpcReply.name().getNodeType());
        final var data = (DOMSourceAnyxmlNode) rpcReply.getChildByArg(NetconfMessageTransformUtil.NETCONF_DATA_NODEID);

        final var diff = DiffBuilder.compare(dataElement.getOwnerDocument())
            .withTest(data.body().getNode())
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }
}
