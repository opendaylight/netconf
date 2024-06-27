/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType.DELETE;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType.WRITE;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240611.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@ExtendWith(MockitoExtension.class)
public class CallHomeMountServiceTest {

    private static final SocketAddress SOCKET_ADDRESS = new InetSocketAddress("127.0.0.1", 12345);
    private static final InstanceIdentifier<Device> IDENTIFIER = InstanceIdentifier.builder(
        NetconfCallhomeServer.class).child(AllowedDevices.class).child(Device.class).build();
    private static final String ID1 = "id1";
    private static final NodeId NODE_ID1 = new NodeId(ID1);
    private static final String ID2 = "id2";

    @Mock
    private CallHomeTopology topology;
    @Mock
    private NetconfClientSessionListener sessionListener;
    @Mock
    private ClientSession sshSession;
    @Mock
    private Channel nettyChannel;
    @Mock
    private CallHomeTlsAuthProvider tlsAuthProvider;
    @Mock
    private CallHomeStatusRecorder statusRecorder;

    private CallHomeMountService service;
    private ListenableFuture<NetconfClientSession> netconfSessionFuture;
    private Node node1;

    @BeforeEach
    void beforeEach() {
        service = new CallHomeMountService(topology);
    }

    @Test
    void sshSessionContextManager() {
        reproduceEnsureNodeId2();
        doReturn(SOCKET_ADDRESS).when(sshSession).getRemoteAddress();
        final var sshSessionContextManager = service.createSshSessionContextManager();

        // id 1 -- netconf layer created
        final var context = sshSessionContextManager.createContext(ID1, sshSession);
        assertNotNull(context);
        assertEquals(ID1, context.id());
        assertEquals(SOCKET_ADDRESS, context.remoteAddress());
        assertSame(sshSession, context.sshSession());
        assertSame(sessionListener, context.netconfSessionListener());
        assertNotNull(context.settableFuture());
        assertSame(netconfSessionFuture, context.settableFuture());
        // id 2 -- netconf layer omitted
        assertNull(sshSessionContextManager.createContext(ID2, sshSession));

        // verify that node is enabled with SSH
        verify(topology, times(1)).enableNode(node1);

        // remove context
        sshSessionContextManager.remove(ID1);
        verify(topology, times(1)).disableNode(eq(NODE_ID1));
    }

    @Test
    void tlsSessionContextManager() {
        reproduceEnsureNodeId2();
        doReturn(SOCKET_ADDRESS).when(nettyChannel).remoteAddress();
        final var tlsSessionContextManager = service.createTlsSessionContextManager(tlsAuthProvider, statusRecorder);

        // id 1 -- netconf layer created
        final var context = tlsSessionContextManager.createContext(ID1, nettyChannel);
        assertNotNull(context);
        assertEquals(ID1, context.id());
        assertSame(nettyChannel, context.nettyChannel());
        assertSame(sessionListener, context.netconfSessionListener());
        assertNotNull(context.settableFuture());
        assertSame(netconfSessionFuture, context.settableFuture());
        // id 2 -- netconf layer omitted
        assertNull(tlsSessionContextManager.createContext(ID2, nettyChannel));

        // verify that node is enabled with TLS
        verify(topology, times(1)).enableNode(node1);

        // remove context
        tlsSessionContextManager.remove(ID1);
        verify(topology, times(1)).disableNode(eq(NODE_ID1));
    }

    @Test
    void testDeletingDeviceFromTopologyAfterModification() {
        final var mockDeviceBeforeAddition = createMockDevice(ID1);
        final var mockObjectModification = mock(DataObjectModification.class);

        // modification of overriding device inside 'allowed-devices' container
        when(mockObjectModification.modificationType()).thenReturn(WRITE);
        when(mockObjectModification.dataBefore()).thenReturn(mockDeviceBeforeAddition);
        service.onAllowedDevicesChanged(List.of(new CustomTreeModification(DataTreeIdentifier.of(
            LogicalDatastoreType.CONFIGURATION, IDENTIFIER), mockObjectModification)));
        verify(topology, times(1)).disableNode(NODE_ID1);

        // modification of deleting device from 'allowed-devices' container
        when(mockObjectModification.modificationType()).thenReturn(DELETE);
        service.onAllowedDevicesChanged(List.of(new CustomTreeModification(DataTreeIdentifier.of(
            LogicalDatastoreType.CONFIGURATION, IDENTIFIER), mockObjectModification)));
        verify(topology, times(2)).disableNode(NODE_ID1);
    }

    @Test
    void testDeletingMultipleDevices() {
        final var mockDevice1 = createMockDevice(ID1);
        final var mockDevice2 = createMockDevice(ID2);

        // Mock DataObjectModifications for each device
        final var mockModification1 = mock(DataObjectModification.class);
        final var mockModification2 = mock(DataObjectModification.class);

        when(mockModification1.modificationType()).thenReturn(DELETE);
        when(mockModification1.dataBefore()).thenReturn(mockDevice1);

        when(mockModification2.modificationType()).thenReturn(DELETE);
        when(mockModification2.dataBefore()).thenReturn(mockDevice2);

        service.onAllowedDevicesChanged(List.of(
            new CustomTreeModification(DataTreeIdentifier.of(LogicalDatastoreType.CONFIGURATION, IDENTIFIER),
                mockModification1),
            new CustomTreeModification(DataTreeIdentifier.of(LogicalDatastoreType.CONFIGURATION, IDENTIFIER),
                mockModification2)
        ));

        verify(topology, times(1)).disableNode(new NodeId(ID1));
        verify(topology, times(1)).disableNode(new NodeId(ID2));
    }

    private void reproduceEnsureNodeId2() {
        /*
         * Reproduce behavior of org.opendaylight.netconf.topology.spi.AbstractNetconfTopology#ensureNode(Node)
         * for ID1 only.
         */
        doAnswer(invocation -> {
            node1 = (Node) invocation.getArguments()[0];
            if (ID1.equals(node1.requireNodeId().getValue())) {
                final var configBuilderFactory = CallHomeMountService.createClientConfigurationBuilderFactory();
                final var config = configBuilderFactory
                    .createClientConfigurationBuilder(node1.requireNodeId(), node1.augmentation(NetconfNode.class))
                    .withSessionListener(sessionListener).build();
                try {
                    netconfSessionFuture = service.createClientFactory().createClient(config);
                } catch (UnsupportedConfigurationException e) {
                    netconfSessionFuture = null;
                }
            } else {
                netconfSessionFuture = null;
            }
            return null;
        }).when(topology).enableNode(any(Node.class));
        doNothing().when(topology).disableNode(any(NodeId.class));
    }

    private static Device createMockDevice(final String deviceId) {
        final var device = mock(Device.class);
        when(device.getUniqueId()).thenReturn(deviceId);
        return device;
    }

    private static class CustomTreeModification implements DataTreeModification<Device> {
        private final DataTreeIdentifier<Device> rootPath;
        private final DataObjectModification<Device> rootNode;

        CustomTreeModification(final DataTreeIdentifier<Device> rootPath,
                final DataObjectModification<Device> rootNode) {
            this.rootPath = rootPath;
            this.rootNode = rootNode;
        }

        @Override
        public @NonNull DataTreeIdentifier<Device> getRootPath() {
            return rootPath;
        }

        @Override
        public DataObjectModification<Device> getRootNode() {
            return rootNode;
        }
    }
}
