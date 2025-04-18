/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.toId;

import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformer;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;

class NetconfToRpcRequestTest extends AbstractBaseSchemasTest {

    private static final String TEST_MODEL_NAMESPACE = "urn:opendaylight:params:xml:ns:yang:controller:md:sal:rpc-test";
    private static final String REVISION = "2014-07-14";
    private static final QName STREAM_NAME = QName.create(TEST_MODEL_NAMESPACE, REVISION, "stream-name");
    private static final QName SUBSCRIBE_RPC_NAME = QName.create(TEST_MODEL_NAMESPACE, REVISION, "subscribe");

    private static final String CONFIG_TEST_NAMESPACE =
            "urn:opendaylight:params:xml:ns:yang:controller:md:sal:test:rpc:config:defs";
    private static final String CONFIG_TEST_REVISION = "2014-07-21";
    private static final QName EDIT_CONFIG_QNAME =
            QName.create(CONFIG_TEST_NAMESPACE, CONFIG_TEST_REVISION, "edit-config");

    static EffectiveModelContext cfgCtx;

    private  NetconfMessageTransformer messageTransformer;

    @BeforeAll
    static void setup() {
        final Collection<? extends Module> notifModules = YangParserTestUtils.parseYangResource(
            "/schemas/rpc-notification-subscription.yang").getModules();
        assertTrue(!notifModules.isEmpty());

        cfgCtx = YangParserTestUtils.parseYangResources(NetconfToRpcRequestTest.class,
            "/schemas/config-test-rpc.yang", "/schemas/rpc-notification-subscription.yang");
    }

    @BeforeEach
    void before() {
        messageTransformer = new NetconfMessageTransformer(DatabindContext.ofModel(cfgCtx), true,
            BASE_SCHEMAS.baseSchemaForCapabilities(NetconfSessionPreferences.fromStrings(Set.of())));
    }

    @Test
    void testUserDefinedRpcCall() {
        final ContainerNode root = ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(toId(SUBSCRIBE_RPC_NAME))
                .withChild(ImmutableNodes.leafNode(STREAM_NAME, "NETCONF"))
                .build();

        final NetconfMessage message = messageTransformer.toRpcRequest(SUBSCRIBE_RPC_NAME, root);
        assertNotNull(message);

        final Document xmlDoc = message.getDocument();
        final org.w3c.dom.Node rpcChild = xmlDoc.getFirstChild();
        assertEquals(rpcChild.getLocalName(), "rpc");

        final org.w3c.dom.Node subscribeName = rpcChild.getFirstChild();
        assertEquals(subscribeName.getLocalName(), "subscribe");

        final org.w3c.dom.Node streamName = subscribeName.getFirstChild();
        assertEquals(streamName.getLocalName(), "stream-name");

    }

    @Test
    void testRpcResponse() throws Exception {
        final NetconfMessage response = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"m-5\">\n"
                        + "<data xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">"
                        + "module schema"
                        + "</data>\n"
                        + "</rpc-reply>\n"
        ));

        // The edit config defined in yang has no output
        assertThrows(IllegalArgumentException.class,
            () -> messageTransformer.toRpcResult(RpcResultBuilder.success(response).build(), EDIT_CONFIG_QNAME));
    }

}
