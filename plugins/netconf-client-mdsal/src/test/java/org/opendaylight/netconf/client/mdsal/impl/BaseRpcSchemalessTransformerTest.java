/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Set;
import javax.xml.transform.dom.DOMSource;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.AbstractBaseSchemasTest;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.copy.config.input.target.ConfigTarget;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.edit.config.input.EditContent;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.edit.config.input.target.config.target.Candidate;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.w3c.dom.Element;
import org.xmlunit.builder.DiffBuilder;

class BaseRpcSchemalessTransformerTest extends AbstractBaseSchemasTest {
    private final BaseRpcSchemalessTransformer transformer =
        new BaseRpcSchemalessTransformer(BASE_SCHEMAS.baseSchemaForCapabilities(
            NetconfSessionPreferences.fromStrings(Set.of())), new MessageCounter());

    @Test
    void toRpcRequest() throws Exception {
        final var msg = transformer.toRpcRequest(EditConfig.QNAME,
            ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_NODEID)
                .withChild(ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(new NodeIdentifier(EditContent.QNAME))
                    .withChild(ImmutableNodes.newAnyxmlBuilder(DOMSource.class)
                        .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_CONFIG_NODEID)
                        .withValue(new DOMSource(XmlUtil.readXmlToDocument(
                            BaseRpcSchemalessTransformerTest.class.getResourceAsStream(
                                "/schemaless/edit-config/container.xml"))
                            .getDocumentElement()))
                        .build())
                    .build())
                .withChild(ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_TARGET_NODEID)
                    .withChild(ImmutableNodes.newChoiceBuilder()
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
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    @Test
    void toRpcResult() throws Exception {
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
        assertEquals(NetconfMessageTransformUtil.NETCONF_OUTPUT_NODEID, rpcReply.name());
        final var data = (DOMSourceAnyxmlNode) rpcReply.getChildByArg(NetconfMessageTransformUtil.NETCONF_DATA_NODEID);

        final var diff = DiffBuilder.compare(dataElement.getOwnerDocument())
            .withTest(data.body().getNode())
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }
}
