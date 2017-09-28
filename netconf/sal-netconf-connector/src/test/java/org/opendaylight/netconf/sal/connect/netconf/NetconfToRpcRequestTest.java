/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toId;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toPath;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;

public class NetconfToRpcRequestTest {

    private static final String TEST_MODEL_NAMESPACE = "urn:opendaylight:params:xml:ns:yang:controller:md:sal:rpc-test";
    private static final String REVISION = "2014-07-14";
    private static final QName INPUT_QNAME = QName.create(TEST_MODEL_NAMESPACE, REVISION, "input");
    private static final QName STREAM_NAME = QName.create(TEST_MODEL_NAMESPACE, REVISION, "stream-name");
    private static final QName SUBSCRIBE_RPC_NAME = QName.create(TEST_MODEL_NAMESPACE, REVISION, "subscribe");

    private static final String CONFIG_TEST_NAMESPACE =
            "urn:opendaylight:params:xml:ns:yang:controller:md:sal:test:rpc:config:defs";
    private static final String CONFIG_TEST_REVISION = "2014-07-21";
    private static final QName EDIT_CONFIG_QNAME =
            QName.create(CONFIG_TEST_NAMESPACE, CONFIG_TEST_REVISION, "edit-config");
    private static final QName GET_QNAME = QName.create(CONFIG_TEST_NAMESPACE, CONFIG_TEST_REVISION, "get");
    private static final QName GET_CONFIG_QNAME =
            QName.create(CONFIG_TEST_NAMESPACE, CONFIG_TEST_REVISION, "get-config");

    static SchemaContext cfgCtx;
    static NetconfMessageTransformer messageTransformer;

    @BeforeClass
    public static void setup() throws Exception {
        final File rpcNotificationSubscriptionModelFile =
                new File(NetconfToRpcRequestTest.class.getResource("/schemas/rpc-notification-subscription.yang").toURI());
        final Set<Module> notifModules =
                YangParserTestUtils.parseYangFiles(rpcNotificationSubscriptionModelFile).getModules();
        assertTrue(!notifModules.isEmpty());

        final List<File> modelsToParse = Lists.newArrayList(
                new File(NetconfToRpcRequestTest.class.getResource("/schemas/config-test-rpc.yang").toURI()),
                new File(NetconfToRpcRequestTest.class.getResource(
                        "/schemas/rpc-notification-subscription.yang").toURI()));

        cfgCtx = YangParserTestUtils.parseYangFiles(modelsToParse);
        messageTransformer = new NetconfMessageTransformer(cfgCtx, true);
    }

    private static LeafNode<Object> buildLeaf(final QName running, final Object value) {
        return Builders.leafBuilder().withNodeIdentifier(toId(running)).withValue(value).build();
    }

    @Test
    public void testUserDefinedRpcCall() throws Exception {
        final DataContainerNodeAttrBuilder<YangInstanceIdentifier.NodeIdentifier, ContainerNode> rootBuilder =
                Builders.containerBuilder();
        rootBuilder.withNodeIdentifier(toId(SUBSCRIBE_RPC_NAME));

        rootBuilder.withChild(buildLeaf(STREAM_NAME, "NETCONF"));
        final ContainerNode root = rootBuilder.build();

        final NetconfMessage message = messageTransformer.toRpcRequest(toPath(SUBSCRIBE_RPC_NAME), root);
        assertNotNull(message);

        final Document xmlDoc = message.getDocument();
        final org.w3c.dom.Node rpcChild = xmlDoc.getFirstChild();
        assertEquals(rpcChild.getLocalName(), "rpc");

        final org.w3c.dom.Node subscribeName = rpcChild.getFirstChild();
        assertEquals(subscribeName.getLocalName(), "subscribe");

        final org.w3c.dom.Node streamName = subscribeName.getFirstChild();
        assertEquals(streamName.getLocalName(), "stream-name");

    }

    // The edit config defined in yang has no output
    @Test(expected = IllegalArgumentException.class)
    public void testRpcResponse() throws Exception {
        final NetconfMessage response = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"m-5\">\n"
                        + "<data xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">"
                        + "module schema"
                        + "</data>\n"
                        + "</rpc-reply>\n"
        ));

        messageTransformer.toRpcResult(response, toPath(EDIT_CONFIG_QNAME));
    }

}
