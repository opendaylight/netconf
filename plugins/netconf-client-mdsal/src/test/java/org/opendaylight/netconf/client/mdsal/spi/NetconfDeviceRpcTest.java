/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.mdsal.dom.api.DOMRpcIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.AbstractBaseSchemasTest;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformer;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfData;
import org.opendaylight.yangtools.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Node;

@ExtendWith(MockitoExtension.class)
class NetconfDeviceRpcTest extends AbstractBaseSchemasTest {
    private static EffectiveModelContext SCHEMA_CONTEXT;

    @Mock
    private DOMRpcAvailabilityListener listener;
    @Mock
    private RemoteDeviceCommunicator communicator;

    private NetconfDeviceRpc rpc;
    private QName type;
    private DOMRpcResult expectedReply;
    private NetconfMessage reply;

    @BeforeAll
    static void beforeClass() {
        SCHEMA_CONTEXT = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfData.class);
    }

    @AfterAll
    static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @BeforeEach
    void setUp() throws Exception {
        final var transformer = new NetconfMessageTransformer(DatabindContext.ofModel(SCHEMA_CONTEXT), true,
            BASE_SCHEMAS.baseSchemaForCapabilities(NetconfSessionPreferences.fromStrings(Set.of())));
        reply = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                        + "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"101\">\n"
                        + "<data>\n"
                        + "</data>\n"
                        + "</rpc-reply>"));
        rpc = new NetconfDeviceRpc(SCHEMA_CONTEXT, communicator, transformer);

        type = QName.create("urn:ietf:params:xml:ns:netconf:base:1.0", "2011-06-01", "get-config");
        expectedReply = transformer.toRpcResult(RpcResultBuilder.success(reply).build(), type);
    }

    @Test
    void testDeadlock() {
        // when rpc is successful, but transformer fails for some reason
        final RpcTransformer<ContainerNode, DOMRpcResult> failingTransformer = mock();
        final RemoteDeviceCommunicator communicatorMock = mock(RemoteDeviceCommunicator.class);
        final NetconfMessage msg = null;
        final RpcResult<NetconfMessage> result = RpcResultBuilder.success(msg).build();
        when(communicatorMock.sendRequest(any())).thenReturn(Futures.immediateFuture(result));
        when(failingTransformer.toRpcResult(any(), any())).thenThrow(new RuntimeException("FAIL"));
        final var failingRpc = new NetconfDeviceRpc(SCHEMA_CONTEXT, communicatorMock, failingTransformer)
            .domRpcService();
        assertThrows(ExecutionException.class, () -> failingRpc.invokeRpc(type, mock(ContainerNode.class)).get());
        assertThrows(ExecutionException.class, () -> failingRpc.invokeRpc(type, null).get());
    }

    @Test
    void testInvokeRpc() throws Exception {
        final var rpcResult = RpcResultBuilder.success(reply).build();
        doReturn(Futures.immediateFuture(rpcResult)).when(communicator).sendRequest(any(NetconfMessage.class));
        final var input = createBaseNetconfNode();
        final DOMRpcResult result = rpc.domRpcService().invokeRpc(type, input).get();
        assertEquals(expectedReply.value().name(), result.value().name());
        assertTrue(resolveNode(expectedReply).isEqualNode(resolveNode(result)));
    }

    @Test
    void testInvokeRpcNoRewrite() throws Exception {
        // Context has the same revision as the requested RPC -> no rewrite happens
        final var rpcResult = RpcResultBuilder.success(reply).build();
        doReturn(Futures.immediateFuture(rpcResult)).when(communicator).sendRequest(any(NetconfMessage.class));
        // input uses the same module+revision as 'type'
        final var input = createBaseNetconfNode();
        final var result = rpc.invokeNetconf(type, input).get();

        // container name (QName) should match exactly (no mapping)
        assertEquals(expectedReply.value().name(), result.value().name());

        // payload must be identical, too
        assertTrue(resolveNode(expectedReply).isEqualNode(resolveNode(result)));
    }

    @Test
    void testInvokeRpcRewriteToLatestAndBack() throws Exception {
        // Build a model context which does NOT include 2011-06-01, but DOES include a later rev (e.g., 2013-09-29).
        // Adjust resource paths/revisions below to whatever you have under test resources.
        final var newerCtx = YangParserTestUtils.parseYangResources(NetconfDeviceRpcTest.class,
            "/schemas/ietf-netconf@2013-09-29.yang", "/schemas/ietf-inet-types@2010-09-24.yang",
            "/schemas/ietf-netconf-acm@2018-02-14.yang", "/schemas/ietf-yang-types@2013-07-15.yang");
        final var transformerNew = new NetconfMessageTransformer(DatabindContext.ofModel(newerCtx), true,
            BASE_SCHEMAS.baseSchemaForCapabilities(NetconfSessionPreferences.fromStrings(Set.of())));
        final var rpcNew = new NetconfDeviceRpc(newerCtx, communicator, transformerNew);

        // Communicator still returns a basic <rpc-reply><data/></rpc-reply>
        final var rpcResult = RpcResultBuilder.success(reply).build();
        doReturn(Futures.immediateFuture(rpcResult)).when(communicator).sendRequest(any(NetconfMessage.class));

        // Call with the older revision (2011-06-01) - this forces the outbound rewrite
        final var oldType = QName.create("urn:ietf:params:xml:ns:netconf:base:1.0", "2011-06-01", "get");
        final var input = createBaseNetconfNode();

        final var result = rpcNew.invokeNetconf(oldType, input).get();

        // Verify the result was mapped back to the ORIGINAL revision on the inbound path
        assertEquals("output", result.value().name().getNodeType().getLocalName());
        assertEquals(oldType.getModule(), result.value().name().getNodeType().getModule());

        // And the payload (anyxml <data/>) survived the round-trip
        final var expectedOld = new NetconfMessageTransformer(DatabindContext.ofModel(
            BindingRuntimeHelpers.createEffectiveModel(IetfNetconfData.class)), true,
            BASE_SCHEMAS.baseSchemaForCapabilities(NetconfSessionPreferences.fromStrings(Set.of())))
            .toRpcResult(RpcResultBuilder.success(reply).build(), oldType);
        assertTrue(resolveNode(expectedOld).isEqualNode(resolveNode(result)));
    }

    private static Node resolveNode(final DOMRpcResult result) {
        DataContainerChild value = result.value()
                .findChildByArg(NetconfMessageTransformUtil.NETCONF_DATA_NODEID).orElseThrow();
        Node node = ((DOMSourceAnyxmlNode)value).body().getNode();
        assertNotNull(node);
        return node;
    }

    @Test
    void testRegisterRpcListener() {
        final ArgumentCaptor<Collection<DOMRpcIdentifier>> argument = ArgumentCaptor.captor();

        rpc.domRpcService().registerRpcListener(listener);

        verify(listener).onRpcAvailable(argument.capture());
        final Collection<DOMRpcIdentifier> argValue = argument.getValue();
        final Collection<? extends RpcDefinition> operations = SCHEMA_CONTEXT.getOperations();
        assertEquals(argValue.size(), operations.size());
        for (RpcDefinition operation : operations) {
            final DOMRpcIdentifier domRpcIdentifier = DOMRpcIdentifier.create(operation.getQName());
            assertTrue(argValue.contains(domRpcIdentifier));
        }
    }

    private static ContainerNode createBaseNetconfNode() {
        return ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create("urn:ietf:params:xml:ns:netconf:base:1.0",
                "2011-06-01", "filter")))
            .build();
    }
}
