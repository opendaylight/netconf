/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.xml.transform.dom.DOMSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.RpcsTimeoutAndRecoveryHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceRpc;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.GetSchema;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;

@ExtendWith(MockitoExtension.class)
class MonitoringSchemaSourceProviderTest {
    private static final ContainerNode TEST_CONTAINER_NODE = MonitoringSchemaSourceProvider
        .createGetSchemaRequest("test", Revision.of("2016-02-08"));
    private static final DefaultDOMRpcResult RPC_RESULT = new DefaultDOMRpcResult(getNode(), Set.of());
    private static final RemoteDeviceId DEVICE_ID = new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));
    // 2 secondes delay for transaction
    private static final long TRANSACTION_DELAY = 2000L;
    private static final SourceIdentifier SOURCE_IDENTIFIER = new SourceIdentifier("test", "2016-02-08");

    @Mock
    EffectiveModelContext modelContext;
    @Mock
    NetconfDeviceCommunicator communicator;
    @Mock
    RpcTransformer<ContainerNode, DOMRpcResult> transformer;
    @Mock
    NetconfMessage message;
    @Mock
    RpcResult<NetconfMessage> rpcResult;

    private MonitoringSchemaSourceProvider provider;
    private NetconfDeviceRpc spyRpc;

    @BeforeEach
    void setUp() {
        doReturn(message).when(transformer).toRpcRequest(GetSchema.QNAME, TEST_CONTAINER_NODE);

        final var rpcsTimeoutHandler = new RpcsTimeoutAndRecoveryHandler(DEVICE_ID, new DefaultNetconfTimer(),
            TRANSACTION_DELAY);
        rpcsTimeoutHandler.setListener(communicator);
        spyRpc = spy(new NetconfDeviceRpc(modelContext, communicator, transformer));
        final var rpcs = rpcsTimeoutHandler.decorateRpcs(spyRpc);
        provider = new MonitoringSchemaSourceProvider(DEVICE_ID, rpcs);
    }

    @Test
    void testGetSource() throws Exception {
        doReturn(Futures.immediateFuture(rpcResult)).when(communicator).sendRequest(message);
        doReturn(RPC_RESULT).when(transformer).toRpcResult(rpcResult, GetSchema.QNAME);

        final var source = provider.getSource(SOURCE_IDENTIFIER).get(3, TimeUnit.SECONDS);
        assertEquals(SOURCE_IDENTIFIER, source.sourceId());

        verify(spyRpc).invokeNetconf(GetSchema.QNAME,
            MonitoringSchemaSourceProvider.createGetSchemaRequest("test", Revision.of("2016-02-08")));
    }

    @Test
    void testGetSourceWithCancellationAfterLongDelay() {
        // Prepare SchemaSource Future reply with no result.
        final var incompleteFuture = SettableFuture.create();
        doReturn(incompleteFuture).when(communicator).sendRequest(message);

        // Assert that get SchemaSource failed with a CancellationException after reaching a max delay of 2 seconds
        final var execException = assertThrows(ExecutionException.class,
            () -> provider.getSource(SOURCE_IDENTIFIER).get(3, TimeUnit.SECONDS));
        assertInstanceOf(CancellationException.class, execException.getCause());

        // Verify RPC invocation
        verify(spyRpc).invokeNetconf(GetSchema.QNAME,
            MonitoringSchemaSourceProvider.createGetSchemaRequest("test", Revision.of("2016-02-08")));
    }

    private static ContainerNode getNode() {
        final var id = new NodeIdentifier(
            QName.create("urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring", "2010-10-04", "output"));
        final var childId = new NodeIdentifier(
            QName.create("urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring", "2010-10-04", "data"));

        var xmlDoc = UntrustedXML.newDocumentBuilder().newDocument();
        var root = xmlDoc.createElement("data");
        root.setTextContent("module test {}");
        return ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(id)
            .withChild(ImmutableNodes.newAnyxmlBuilder(DOMSource.class)
                .withNodeIdentifier(childId)
                .withValue(new DOMSource(root))
                .build())
            .build();
    }
}
