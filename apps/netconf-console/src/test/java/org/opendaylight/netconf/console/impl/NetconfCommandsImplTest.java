/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.console.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.dom.adapter.test.ConcurrentDataBrokerTestCustomizer;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;
import org.opendaylight.netconf.console.utils.NetconfConsoleUtils;
import org.opendaylight.netconf.console.utils.NetconfIidFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240104.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240104.connection.oper.AvailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240104.connection.oper.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240104.connection.oper.available.capabilities.AvailableCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.Uint16;

class NetconfCommandsImplTest {
    private static final String NODE_ID = "NodeID";
    private static final String IP = "192.168.1.1";
    private static final int PORT = 1234;
    private static final ConnectionStatus CONN_STATUS = ConnectionStatus.Connected;
    private static final String CAP_PREFIX = "prefix";

    private static BindingRuntimeContext RUNTIME_CONTEXT =
        BindingRuntimeHelpers.createRuntimeContext(TopologyNetconf.class);

    private final DataBroker dataBroker;
    private final NetconfCommandsImpl netconfCommands;

    NetconfCommandsImplTest() {
        final var customizer = new ConcurrentDataBrokerTestCustomizer(true);
        dataBroker = customizer.createDataBroker();
        customizer.updateSchema(RUNTIME_CONTEXT);
        netconfCommands = new NetconfCommandsImpl(dataBroker);
    }

    @Test
    void testListDevice() throws Exception {
        createTopology(LogicalDatastoreType.OPERATIONAL);

        final var map = netconfCommands.listDevices();
        // FIXME: WHAT?!
        map.containsKey(NetconfConsoleConstants.NETCONF_ID);
        assertTrue(map.containsKey(NODE_ID));

        final var mapNode = map.get(NODE_ID);
        assertBaseNodeAttributes(mapNode);
    }

    @Test
    void testShowDevice() throws Exception {
        createTopology(LogicalDatastoreType.OPERATIONAL);

        final var mapCorrect = netconfCommands.showDevice(IP, String.valueOf(PORT));
        // FIXME: WHAT?!
        mapCorrect.containsKey(NetconfConsoleConstants.NETCONF_ID);
        assertTrue(mapCorrect.containsKey(NODE_ID));

        assertBaseNodeAttributesList(mapCorrect.get(NODE_ID));

        final var mapWrongPort = netconfCommands.showDevice(IP, "1");
        assertFalse(mapWrongPort.containsKey(NODE_ID));

        final var mapWrongIP = netconfCommands.showDevice("1.1.1.1", String.valueOf(PORT));
        assertFalse(mapWrongIP.containsKey(NODE_ID));

        final var mapId = netconfCommands.showDevice(NODE_ID);
        assertTrue(mapId.containsKey(NODE_ID));
        assertBaseNodeAttributesList(mapId.get(NODE_ID));
    }

    @Test
    void testConnectDisconnectDevice() throws Exception {
        final var netconfNode = new NetconfNodeBuilder()
            .setPort(new PortNumber(Uint16.valueOf(7777)))
            .setHost(new Host(new IpAddress(new Ipv4Address("10.10.1.1"))))
            .build();

        createTopology(LogicalDatastoreType.CONFIGURATION);
        netconfCommands.connectDevice(netconfNode, "netconf-ID");

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            final var topology = NetconfConsoleUtils.read(LogicalDatastoreType.CONFIGURATION,
                    NetconfIidFactory.NETCONF_TOPOLOGY_IID, dataBroker);
            final var nodes = topology.nonnullNode().values();
            if (nodes.size() != 2) {
                return false;
            }

            final var storedNode = nodes.stream()
                .filter(node -> node.key().getNodeId().getValue().equals("netconf-ID"))
                .findFirst();

            assertTrue(storedNode.isPresent());

            final var storedNetconfNode = storedNode.orElseThrow().augmentation(NetconfNode.class);
            assertEquals(7777, storedNetconfNode.getPort().getValue().longValue());
            assertEquals("10.10.1.1", storedNetconfNode.getHost().getIpAddress().getIpv4Address().getValue());
            return true;
        });

        netconfCommands.disconnectDevice("netconf-ID");

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            final var topologyDeleted = NetconfConsoleUtils.read(LogicalDatastoreType.CONFIGURATION,
                    NetconfIidFactory.NETCONF_TOPOLOGY_IID, dataBroker);
            final var nodesDeleted = topologyDeleted.nonnullNode().values();
            if (nodesDeleted.size() != 1) {
                return false;
            }

            assertEquals(Optional.empty(), nodesDeleted.stream()
                .filter(node -> node.key().getNodeId().getValue().equals("netconf-ID"))
                .findFirst());
            return true;
        });
    }

    @Test
    void testUpdateDevice() throws Exception {
        //We need both, read data from OPERATIONAL DS and update data in CONFIGURATIONAL DS
        createTopology(LogicalDatastoreType.OPERATIONAL);
        createTopology(LogicalDatastoreType.CONFIGURATION);

        netconfCommands.updateDevice(NODE_ID, "admin", "admin", Map.of(
            NetconfConsoleConstants.NETCONF_IP, "7.7.7.7",
            NetconfConsoleConstants.TCP_ONLY, "true",
            NetconfConsoleConstants.SCHEMALESS, "true"));

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            final var topology = NetconfConsoleUtils.read(LogicalDatastoreType.CONFIGURATION,
                    NetconfIidFactory.NETCONF_TOPOLOGY_IID, dataBroker);
            final var nodes = topology.nonnullNode().values();
            if (nodes.size() != 1) {
                return false;
            }

            final var storedNode = nodes.stream()
                .filter(node -> node.key().getNodeId().getValue().equals(NODE_ID))
                .findFirst();
            assertTrue(storedNode.isPresent());

            final var storedNetconfNode = storedNode.orElseThrow().augmentation(NetconfNode.class);
            assertEquals("7.7.7.7", storedNetconfNode.getHost().getIpAddress().getIpv4Address().getValue());
            return true;
        });
    }

    private void createTopology(final LogicalDatastoreType dataStoreType) throws Exception {
        final var node = getNetconfNode(NODE_ID, IP, PORT, CONN_STATUS, CAP_PREFIX);
        final var topology = new TopologyBuilder()
            .withKey(new TopologyKey(new TopologyId(TopologyNetconf.QNAME.getLocalName())))
            .setTopologyId(new TopologyId(TopologyNetconf.QNAME.getLocalName()))
            .setNode(BindingMap.of(node))
            .build();

        final var writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.put(dataStoreType, NetconfIidFactory.NETCONF_TOPOLOGY_IID, topology);
        writeTransaction.commit().get(2, TimeUnit.SECONDS);
    }

    private static Node getNetconfNode(final String nodeIdent, final String ip, final int portNumber,
            final ConnectionStatus cs, final String notificationCapabilityPrefix) {
        return new NodeBuilder()
            .setNodeId(new NodeId(nodeIdent))
            .addAugmentation(new NetconfNodeBuilder()
                .setConnectionStatus(cs)
                .setHost(new Host(new IpAddress(new Ipv4Address(ip))))
                .setPort(new PortNumber(Uint16.valueOf(portNumber)))
                .setAvailableCapabilities(new AvailableCapabilitiesBuilder()
                    .setAvailableCapability(List.of(new AvailableCapabilityBuilder()
                        .setCapabilityOrigin(AvailableCapability.CapabilityOrigin.UserDefined)
                        .setCapability(notificationCapabilityPrefix + "_availableCapabilityString1")
                        .build()))
                    .build())
                .build())
            .build();
    }

    private static void assertBaseNodeAttributes(final Map<?, ?> mapNode) {
        assertTrue(mapNode.containsKey(NetconfConsoleConstants.NETCONF_ID));
        assertTrue(mapNode.containsKey(NetconfConsoleConstants.NETCONF_IP));
        assertTrue(mapNode.containsKey(NetconfConsoleConstants.NETCONF_PORT));
        assertTrue(mapNode.containsKey(NetconfConsoleConstants.STATUS));

        assertEquals(NODE_ID, mapNode.get(NetconfConsoleConstants.NETCONF_ID));
        assertEquals(IP, mapNode.get(NetconfConsoleConstants.NETCONF_IP));
        assertEquals(String.valueOf(PORT), mapNode.get(NetconfConsoleConstants.NETCONF_PORT));
        assertEquals(CONN_STATUS.name().toLowerCase(), mapNode.get(NetconfConsoleConstants.STATUS));
    }

    private static void assertBaseNodeAttributesList(final Map<?, ?> mapNode) {
        assertTrue(mapNode.containsKey(NetconfConsoleConstants.NETCONF_ID));
        assertTrue(mapNode.containsKey(NetconfConsoleConstants.NETCONF_IP));
        assertTrue(mapNode.containsKey(NetconfConsoleConstants.NETCONF_PORT));
        assertTrue(mapNode.containsKey(NetconfConsoleConstants.STATUS));

        assertEquals(List.of(NODE_ID), mapNode.get(NetconfConsoleConstants.NETCONF_ID));
        assertEquals(List.of(IP), mapNode.get(NetconfConsoleConstants.NETCONF_IP));
        assertEquals(List.of(String.valueOf(PORT)), mapNode.get(NetconfConsoleConstants.NETCONF_PORT));
        assertEquals(List.of(CONN_STATUS.name()), mapNode.get(NetconfConsoleConstants.STATUS));
    }
}
