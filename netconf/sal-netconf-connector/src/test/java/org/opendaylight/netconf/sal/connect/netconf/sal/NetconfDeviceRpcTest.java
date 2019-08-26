/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.mdsal.dom.api.DOMRpcIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.$YangModuleInfoImpl;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.w3c.dom.Node;

public class NetconfDeviceRpcTest {
    private static SchemaContext SCHEMA_CONTEXT;

    @Mock
    private DOMRpcAvailabilityListener listener;
    @Mock
    private RemoteDeviceCommunicator<NetconfMessage> communicator;

    private NetconfDeviceRpc rpc;
    private SchemaPath path;
    private DOMRpcResult expectedReply;

    @BeforeClass
    public static void beforeClass() {
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        moduleInfoBackedContext.addModuleInfos(Collections.singleton($YangModuleInfoImpl.getInstance()));
        SCHEMA_CONTEXT = moduleInfoBackedContext.tryToCreateSchemaContext().get();
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        NetconfMessageTransformer transformer = new NetconfMessageTransformer(SCHEMA_CONTEXT, true);
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

        path = SchemaPath
                .create(true, QName.create("urn:ietf:params:xml:ns:netconf:base:1.0", "2011-06-01", "get-config"));
        expectedReply = transformer.toRpcResult(reply, path);
    }

    @Test
    public void testInvokeRpc() throws Exception {
        NormalizedNode<?, ?> input = createNode("urn:ietf:params:xml:ns:netconf:base:1.0", "2011-06-01", "filter");
        final DOMRpcResult result = rpc.invokeRpc(path, input).get();
        Assert.assertEquals(expectedReply.getResult().getIdentifier(), result.getResult().getIdentifier());
        Assert.assertEquals(resolveNode(expectedReply), resolveNode(result));
    }

    private static Node resolveNode(final DOMRpcResult result) {
        DataContainerChild<?, ?> value = ((ContainerNode) result.getResult())
                .getChild(NetconfMessageTransformUtil.NETCONF_DATA_NODEID).get();
        Node node = ((AnyXmlNode)value).getValue().getNode();
        assertNotNull(node);
        return node;
    }

    @Test
    public void testRegisterRpcListener() throws Exception {
        ArgumentCaptor<Collection> argument = ArgumentCaptor.forClass(Collection.class);

        rpc.registerRpcListener(listener);

        verify(listener).onRpcAvailable(argument.capture());
        final Collection<DOMRpcIdentifier> argValue = argument.getValue();
        final Set<RpcDefinition> operations = SCHEMA_CONTEXT.getOperations();
        Assert.assertEquals(argValue.size(), operations.size());
        for (RpcDefinition operation : operations) {
            final DOMRpcIdentifier domRpcIdentifier = DOMRpcIdentifier.create(operation.getPath());
            Assert.assertTrue(argValue.contains(domRpcIdentifier));

        }
    }

    private static ContainerNode createNode(final String namespace, final String date, final String localName) {
        return Builders.containerBuilder().withNodeIdentifier(
                new YangInstanceIdentifier.NodeIdentifier(QName.create(namespace, date, localName))).build();
    }
}
