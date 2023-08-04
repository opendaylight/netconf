/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.mdsal.dom.api.DOMRpcIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.AbstractBaseSchemasTest;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.w3c.dom.Node;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceRpcTest extends AbstractBaseSchemasTest {
    private static EffectiveModelContext SCHEMA_CONTEXT;

    @Mock
    private DOMRpcAvailabilityListener listener;
    @Mock
    private RemoteDeviceCommunicator communicator;

    private NetconfDeviceRpc rpc;
    private QName type;
    private DOMRpcResult expectedReply;

    @BeforeClass
    public static void beforeClass() {
        SCHEMA_CONTEXT = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfService.class);
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @Before
    public void setUp() throws Exception {
        NetconfMessageTransformer transformer = new NetconfMessageTransformer(
            MountPointContext.of(SCHEMA_CONTEXT), true, BASE_SCHEMAS.getBaseSchema());
        final NetconfMessage reply = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                        + "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"101\">\n"
                        + "<data>\n"
                        + "</data>\n"
                        + "</rpc-reply>"));
        RpcResult<NetconfMessage> result = RpcResultBuilder.success(reply).build();
        doReturn(Futures.immediateFuture(result))
                .when(communicator).sendRequest(any(NetconfMessage.class), any(QName.class));
        rpc = new NetconfDeviceRpc(SCHEMA_CONTEXT, communicator, transformer);

        type = QName.create("urn:ietf:params:xml:ns:netconf:base:1.0", "2011-06-01", "get-config");
        expectedReply = transformer.toRpcResult(RpcResultBuilder.success(reply).build(), type);
    }

    @Test
    public void testDeadlock() throws Exception {
        // when rpc is successful, but transformer fails for some reason
        final RpcTransformer<ContainerNode, DOMRpcResult> failingTransformer = mock(RpcTransformer.class);
        final RemoteDeviceCommunicator communicatorMock = mock(RemoteDeviceCommunicator.class);
        final NetconfMessage msg = null;
        final RpcResult<NetconfMessage> result = RpcResultBuilder.success(msg).build();
        when(communicatorMock.sendRequest(any(), any())).thenReturn(Futures.immediateFuture(result));
        when(failingTransformer.toRpcResult(any(), any())).thenThrow(new RuntimeException("FAIL"));
        final NetconfDeviceRpc failingRpc = new NetconfDeviceRpc(SCHEMA_CONTEXT, communicatorMock, failingTransformer);
        assertThrows(ExecutionException.class, () -> failingRpc.invokeRpc(type, mock(ContainerNode.class)).get());
        assertThrows(ExecutionException.class, () -> failingRpc.invokeRpc(type, null).get());
    }

    @Test
    public void testInvokeRpc() throws Exception {
        ContainerNode input = createNode("urn:ietf:params:xml:ns:netconf:base:1.0", "2011-06-01", "filter");
        final DOMRpcResult result = rpc.invokeRpc(type, input).get();
        assertEquals(expectedReply.value().name(), result.value().name());
        assertEquals(resolveNode(expectedReply), resolveNode(result));
    }

    private static Node resolveNode(final DOMRpcResult result) {
        DataContainerChild value = result.value()
                .findChildByArg(NetconfMessageTransformUtil.NETCONF_DATA_NODEID).orElseThrow();
        Node node = ((DOMSourceAnyxmlNode)value).body().getNode();
        assertNotNull(node);
        return node;
    }

    @Test
    public void testRegisterRpcListener() throws Exception {
        ArgumentCaptor<Collection> argument = ArgumentCaptor.forClass(Collection.class);

        rpc.registerRpcListener(listener);

        verify(listener).onRpcAvailable(argument.capture());
        final Collection<DOMRpcIdentifier> argValue = argument.getValue();
        final Collection<? extends RpcDefinition> operations = SCHEMA_CONTEXT.getOperations();
        assertEquals(argValue.size(), operations.size());
        for (RpcDefinition operation : operations) {
            final DOMRpcIdentifier domRpcIdentifier = DOMRpcIdentifier.create(operation.getQName());
            assertTrue(argValue.contains(domRpcIdentifier));
        }
    }

    private static ContainerNode createNode(final String namespace, final String date, final String localName) {
        return Builders.containerBuilder().withNodeIdentifier(
                new NodeIdentifier(QName.create(namespace, date, localName))).build();
    }
}
