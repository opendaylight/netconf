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
import java.lang.annotation.Annotation;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataObjectDeleted;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataObjectWritten;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.NetconfNodeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.allowed.devices.DeviceKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;

@ExtendWith(MockitoExtension.class)
class CallHomeMountServiceTest {

    private static final SocketAddress SOCKET_ADDRESS = new InetSocketAddress("127.0.0.1", 12345);
    private static final String ID1 = "id1";
    private static final NodeId NODE_ID1 = new NodeId(ID1);
    private static final String ID2 = "id2";
    private static final DataObjectIdentifier<Device> IDENTIFIER =
        DataObjectIdentifier.builder(NetconfCallhomeServer.class)
            .child(AllowedDevices.class)
            .child(Device.class, new DeviceKey(ID1))
            .build();

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
        service = new CallHomeMountService(topology, defaultConfig());
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
        final DataObjectWritten<Device> mockObjectWritten = mock();

        // modification of overriding device inside 'allowed-devices' container
        when(mockObjectWritten.modificationType()).thenReturn(WRITE);
        when(mockObjectWritten.dataBefore()).thenReturn(mockDeviceBeforeAddition);
        service.onAllowedDevicesChanged(List.of(new CustomTreeModification(LogicalDatastoreType.CONFIGURATION,
            IDENTIFIER, mockObjectWritten)));
        verify(topology, times(1)).disableNode(NODE_ID1);

        // modification of deleting device from 'allowed-devices' container
        final DataObjectDeleted<Device> mockObjectDeleted = mock();
        when(mockObjectDeleted.modificationType()).thenReturn(DELETE);
        when(mockObjectDeleted.dataBefore()).thenReturn(mockDeviceBeforeAddition);
        service.onAllowedDevicesChanged(List.of(
            new CustomTreeModification(LogicalDatastoreType.CONFIGURATION, IDENTIFIER, mockObjectDeleted)));
        verify(topology, times(2)).disableNode(NODE_ID1);
    }

    @Test
    void testDeletingMultipleDevices() {
        final var mockDevice1 = createMockDevice(ID1);
        final var mockDevice2 = createMockDevice(ID2);

        // Mock DataObjectModifications for each device
        final DataObjectDeleted<Device> mockModification1 = mock();
        final DataObjectDeleted<Device> mockModification2 = mock();

        when(mockModification1.modificationType()).thenReturn(DELETE);
        when(mockModification1.dataBefore()).thenReturn(mockDevice1);

        when(mockModification2.modificationType()).thenReturn(DELETE);
        when(mockModification2.dataBefore()).thenReturn(mockDevice2);

        service.onAllowedDevicesChanged(List.of(
            new CustomTreeModification(LogicalDatastoreType.CONFIGURATION, IDENTIFIER, mockModification1),
            new CustomTreeModification(LogicalDatastoreType.CONFIGURATION, IDENTIFIER, mockModification2)
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
                final var configBuilderFactory = service.createClientConfigurationBuilderFactory();
                final var config = configBuilderFactory
                    .createClientConfigurationBuilder(node1.requireNodeId(),
                        node1.augmentation(NetconfNodeAugment.class).getNetconfNode())
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

    private static CallHomeMountService.Configuration defaultConfig() {
        return new CallHomeMountService.Configuration() {
            @Override
            public String host() {
                return "0.0.0.0";
            }

            @Override
            public int ssh$_$port() {
                return 4334;
            }

            @Override
            public int tls$_$port() {
                return 4335;
            }

            @Override
            public int connection$_$timeout$_$millis() {
                return 10_000;
            }

            @Override
            public int max$_$connections() {
                return 64;
            }

            @Override
            public int keep$_$alive$_$delay() {
                return 120;
            }

            @Override
            public int request$_$timeout$_$millis() {
                return 60000;
            }

            @Override
            public int min$_$backoff$_$millis() {
                return 2000;
            }

            @Override
            public int max$_$backoff$_$millis() {
                return 1800000;
            }

            @Override
            public double backoff$_$multiplier() {
                return 1.5;
            }

            @Override
            public double backoff$_$jitter() {
                return 0.1;
            }

            @Override
            public int concurrent$_$rpc$_$limit() {
                return 0;
            }

            @Override
            public int max$_$connection$_$attempts() {
                return 0;
            }

            @Override
            public boolean schemaless() {
                return false;
            }

            @Override
            public int actor$_$response$_$wait$_$time() {
                return 5;
            }

            @Override
            public boolean lock$_$datastore() {
                return true;
            }

            @Override
            public boolean reconnect$_$on$_$changed$_$schema() {
                return false;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return null;
            }
        };
    }

    private record CustomTreeModification(LogicalDatastoreType datastore, DataObjectIdentifier<Device> path,
        DataObjectModification<Device> rootNode) implements DataTreeModification<Device> {

        @Override
        public DataObjectModification<Device> getRootNode() {
            return rootNode;
        }

        @Override
        public LogicalDatastoreType datastore() {
            return datastore;
        }

        @Override
        public @NonNull DataObjectIdentifier<Device> path() {
            return path;
        }
    }
}
