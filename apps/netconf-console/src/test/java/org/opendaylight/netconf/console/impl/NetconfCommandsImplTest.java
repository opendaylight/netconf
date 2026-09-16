/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.console.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.dom.adapter.test.ConcurrentDataBrokerTestCustomizer;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;
import org.opendaylight.netconf.console.utils.NetconfConsoleUtils;
import org.opendaylight.netconf.console.utils.NetconfIidFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.connection.oper.AvailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.connection.oper.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.connection.oper.available.capabilities.AvailableCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.NetconfNodeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.NetconfNodeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.yangtools.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yangtools.binding.util.BindingMap;
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
    void testListDevice() {
        putOperTopology();

        final var map = netconfCommands.listDevices();
        // FIXME: WHAT?!
        map.containsKey(NetconfConsoleConstants.NETCONF_ID);
        assertTrue(map.containsKey(NODE_ID));

        final var mapNode = map.get(NODE_ID);
        assertBaseNodeAttributes(mapNode);
    }

    @Test
    void testShowDevice() {
        putOperTopology();

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
    void testConnectDisconnectDevice() {
        final var netconfNode = new NetconfNodeBuilder()
            .setPort(new PortNumber(Uint16.valueOf(7777)))
            .setHost(new Host(new IpAddress(new Ipv4Address("10.10.1.1"))))
            .setCredentials(new LoginPwUnencryptedBuilder()
                .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                    .setUsername("testuser")
                    .setPassword("testpassword")
                    .build())
                .build())
            .build();

        putConfigTopology();
        netconfCommands.connectDevice(netconfNode, "netconf-ID");

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            final var topology = NetconfConsoleUtils.read(LogicalDatastoreType.CONFIGURATION,
                    NetconfIidFactory.NETCONF_TOPOLOGY_IID, dataBroker);
            final var nodes = topology.nonnullNode().values();
            assertEquals(2, nodes.size());

            final var storedAugment = nodes.stream()
                .filter(node -> node.key().getNodeId().getValue().equals("netconf-ID"))
                .findFirst()
                .orElseThrow()
                .augmentation(NetconfNodeAugment.class);
            assertNotNull(storedAugment);

            final var storedNetconfNode = storedAugment.getNetconfNode();
            assertEquals(Uint16.valueOf(7777), storedNetconfNode.getPort().getValue());
            assertEquals(new Ipv4Address("10.10.1.1"), storedNetconfNode.getHost().getIpAddress().getIpv4Address());
        });

        netconfCommands.disconnectDevice("netconf-ID");

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            final var topologyDeleted = NetconfConsoleUtils.read(LogicalDatastoreType.CONFIGURATION,
                    NetconfIidFactory.NETCONF_TOPOLOGY_IID, dataBroker);
            final var nodesDeleted = topologyDeleted.nonnullNode().values();
            assertEquals(1, nodesDeleted.size());
            assertEquals(Optional.empty(), nodesDeleted.stream()
                .filter(node -> node.key().getNodeId().getValue().equals("netconf-ID"))
                .findFirst());
        });
    }

    @Test
    void testUpdateDevice() {
        //We need both, read data from OPERATIONAL DS and update data in CONFIGURATIONAL DS
        putConfigTopology();
        putOperTopology();

        netconfCommands.updateDevice(NODE_ID, "admin", "admin", Map.of(
            NetconfConsoleConstants.NETCONF_IP, "7.7.7.7",
            NetconfConsoleConstants.TCP_ONLY, "true",
            NetconfConsoleConstants.SCHEMALESS, "true"));

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            final var topology = NetconfConsoleUtils.read(LogicalDatastoreType.CONFIGURATION,
                    NetconfIidFactory.NETCONF_TOPOLOGY_IID, dataBroker);
            final var nodes = topology.nonnullNode().values();
            assertEquals(1, nodes.size());

            final var storedAugment = nodes.stream()
                .filter(node -> node.key().getNodeId().getValue().equals(NODE_ID))
                .findFirst()
                .orElseThrow()
                .augmentation(NetconfNodeAugment.class);
            assertNotNull(storedAugment);

            final var storedNetconfNode = storedAugment.getNetconfNode();
            assertEquals(new Ipv4Address("7.7.7.7"), storedNetconfNode.getHost().getIpAddress().getIpv4Address());
        });
    }

    private void putConfigTopology() {
        putTopology(LogicalDatastoreType.CONFIGURATION, new TopologyBuilder()
            .withKey(new TopologyKey(new TopologyId(TopologyNetconf.QNAME.getLocalName())))
            .setTopologyId(new TopologyId(TopologyNetconf.QNAME.getLocalName()))
            .setNode(BindingMap.of(new NodeBuilder()
                .setNodeId(new NodeId(NODE_ID))
                .addAugmentation(new NetconfNodeAugmentBuilder()
                    .setNetconfNode(new NetconfNodeBuilder()
                        .setHost(new Host(new IpAddress(new Ipv4Address(IP))))
                        .setPort(new PortNumber(Uint16.valueOf(PORT)))
                        .setCredentials(new LoginPwUnencryptedBuilder()
                            .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                                .setUsername("test")
                                .setPassword("test")
                                .build())
                            .build())
                        .build())
                    .build())
                .build()))
            .build());
    }

    private void putOperTopology() {
        putTopology(LogicalDatastoreType.OPERATIONAL, new TopologyBuilder()
            .withKey(new TopologyKey(new TopologyId(TopologyNetconf.QNAME.getLocalName())))
            .setTopologyId(new TopologyId(TopologyNetconf.QNAME.getLocalName()))
            .setNode(BindingMap.of(new NodeBuilder()
                .setNodeId(new NodeId(NODE_ID))
                .addAugmentation(new NetconfNodeAugmentBuilder()
                    .setNetconfNode(new NetconfNodeBuilder()
                        .setConnectionStatus(CONN_STATUS)
                        .setHost(new Host(new IpAddress(new Ipv4Address(IP))))
                        .setPort(new PortNumber(Uint16.valueOf(PORT)))
                        .setAvailableCapabilities(new AvailableCapabilitiesBuilder()
                            .setAvailableCapability(List.of(new AvailableCapabilityBuilder()
                                .setCapabilityOrigin(AvailableCapability.CapabilityOrigin.UserDefined)
                                .setCapability(CAP_PREFIX + "_availableCapabilityString1")
                                .build()))
                            .build())
                        .build())
                    .build())
                .build()))
            .build());
    }

    private void putTopology(final LogicalDatastoreType datastore, final Topology topology) {
        final var commitInfo = assertDoesNotThrow(() -> {
            final var tx = dataBroker.newWriteOnlyTransaction();
            tx.put(datastore, NetconfIidFactory.NETCONF_TOPOLOGY_IID, topology);
            return tx.commit().get(2, TimeUnit.SECONDS);
        });
        assertNotNull(commitInfo);
    }

    // FIXME: use AssertJ instead
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
