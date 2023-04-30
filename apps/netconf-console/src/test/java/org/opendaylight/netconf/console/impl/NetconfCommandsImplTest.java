/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.console.impl;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.AvailableCapabilities;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.AvailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.available.capabilities.AvailableCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;

public class NetconfCommandsImplTest {
    private static final String NODE_ID = "NodeID";
    private static final String IP = "192.168.1.1";
    private static final int PORT = 1234;
    private static final ConnectionStatus CONN_STATUS = ConnectionStatus.Connected;
    private static final String CAP_PREFIX = "prefix";

    private static BindingRuntimeContext RUNTIME_CONTEXT;

    private DataBroker dataBroker;
    private NetconfCommandsImpl netconfCommands;

    @BeforeClass
    public static void beforeClass() {
        RUNTIME_CONTEXT = BindingRuntimeHelpers.createRuntimeContext(TopologyNetconf.class);
    }

    @AfterClass
    public static void afterClass() {
        RUNTIME_CONTEXT = null;
    }

    @Before
    public void setUp() throws Exception {
        ConcurrentDataBrokerTestCustomizer customizer = new ConcurrentDataBrokerTestCustomizer(true);
        dataBroker = customizer.createDataBroker();
        customizer.updateSchema(RUNTIME_CONTEXT);

        netconfCommands = new NetconfCommandsImpl(dataBroker);
    }

    @Test
    public void testListDevice() throws TimeoutException, InterruptedException, ExecutionException {
        createTopology(LogicalDatastoreType.OPERATIONAL);

        final Map<?, ?> map = netconfCommands.listDevices();
        map.containsKey(NetconfConsoleConstants.NETCONF_ID);
        assertTrue(map.containsKey(NODE_ID));

        final Map<?, ?> mapNode = (Map<?, ?>) map.get(NODE_ID);
        assertBaseNodeAttributes(mapNode);
    }

    @Test
    public void testShowDevice() throws TimeoutException, InterruptedException, ExecutionException {
        createTopology(LogicalDatastoreType.OPERATIONAL);

        final Map<?, ?> mapCorrect = netconfCommands.showDevice(IP, String.valueOf(PORT));
        mapCorrect.containsKey(NetconfConsoleConstants.NETCONF_ID);
        assertTrue(mapCorrect.containsKey(NODE_ID));

        assertBaseNodeAttributesImmutableList((Map<?, ?>) mapCorrect.get(NODE_ID));

        final Map<?, ?> mapWrongPort = netconfCommands.showDevice(IP, "1");
        assertFalse(mapWrongPort.containsKey(NODE_ID));

        final Map<?, ?> mapWrongIP = netconfCommands.showDevice("1.1.1.1", String.valueOf(PORT));
        assertFalse(mapWrongIP.containsKey(NODE_ID));

        final Map<?, ?> mapId = netconfCommands.showDevice(NODE_ID);
        assertTrue(mapId.containsKey(NODE_ID));
        assertBaseNodeAttributesImmutableList((Map<?, ?>) mapId.get(NODE_ID));
    }

    @Test
    public void testConnectDisconnectDevice() throws InterruptedException, TimeoutException, ExecutionException {
        final NetconfNode netconfNode = new NetconfNodeBuilder()
                .setPort(new PortNumber(Uint16.valueOf(7777)))
                .setHost(new Host(new IpAddress(new Ipv4Address("10.10.1.1"))))
                .build();

        createTopology(LogicalDatastoreType.CONFIGURATION);
        netconfCommands.connectDevice(netconfNode, "netconf-ID");

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            final Topology topology = NetconfConsoleUtils.read(LogicalDatastoreType.CONFIGURATION,
                    NetconfIidFactory.NETCONF_TOPOLOGY_IID, dataBroker);
            final Collection<Node> nodes = topology.nonnullNode().values();
            if (nodes.size() != 2) {
                return false;
            }

            final Optional<Node> storedNode = nodes.stream().filter(node ->
                    node.key().getNodeId().getValue().equals("netconf-ID")).findFirst();

            assertTrue(storedNode.isPresent());

            NetconfNode storedNetconfNode = storedNode.orElseThrow().augmentation(NetconfNode.class);
            assertEquals(7777, storedNetconfNode.getPort().getValue().longValue());
            assertEquals("10.10.1.1", storedNetconfNode.getHost().getIpAddress().getIpv4Address().getValue());
            return true;
        });

        netconfCommands.disconnectDevice("netconf-ID");

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            final Topology topologyDeleted = NetconfConsoleUtils.read(LogicalDatastoreType.CONFIGURATION,
                    NetconfIidFactory.NETCONF_TOPOLOGY_IID, dataBroker);
            final Collection<Node> nodesDeleted = topologyDeleted.nonnullNode().values();
            if (nodesDeleted.size() != 1) {
                return false;
            }

            final Optional<Node> storedNodeDeleted = nodesDeleted.stream().filter(node ->
                    node.key().getNodeId().getValue().equals("netconf-ID")).findFirst();

            assertFalse(storedNodeDeleted.isPresent());
            return true;
        });
    }

    @Test
    public void testUpdateDevice() throws TimeoutException, InterruptedException, ExecutionException {
        //We need both, read data from OPERATIONAL DS and update data in CONFIGURATIONAL DS
        createTopology(LogicalDatastoreType.OPERATIONAL);
        createTopology(LogicalDatastoreType.CONFIGURATION);

        final Map<String, String> update = new HashMap<>();
        update.put(NetconfConsoleConstants.NETCONF_IP, "7.7.7.7");
        update.put(NetconfConsoleConstants.TCP_ONLY, "true");
        update.put(NetconfConsoleConstants.SCHEMALESS, "true");

        netconfCommands.updateDevice(NODE_ID, "admin", "admin", update);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            final Topology topology = NetconfConsoleUtils.read(LogicalDatastoreType.CONFIGURATION,
                    NetconfIidFactory.NETCONF_TOPOLOGY_IID, dataBroker);
            final Collection<Node> nodes = topology.nonnullNode().values();
            if (nodes.size() != 1) {
                return false;
            }

            final Optional<Node> storedNode = nodes.stream().filter(node ->
                    node.key().getNodeId().getValue().equals(NODE_ID)).findFirst();
            assertTrue(storedNode.isPresent());

            NetconfNode storedNetconfNode = storedNode.orElseThrow().augmentation(NetconfNode.class);
            assertEquals("7.7.7.7", storedNetconfNode.getHost().getIpAddress().getIpv4Address().getValue());
            return true;
        });
    }

    private void createTopology(final LogicalDatastoreType dataStoreType)
            throws TimeoutException, InterruptedException, ExecutionException {
        final Node node = getNetconfNode(NODE_ID, IP, PORT, CONN_STATUS, CAP_PREFIX);

        final Topology topology = new TopologyBuilder()
                .withKey(new TopologyKey(new TopologyId(TopologyNetconf.QNAME.getLocalName())))
                .setTopologyId(new TopologyId(TopologyNetconf.QNAME.getLocalName()))
                .setNode(ImmutableMap.of(node.key(), node)).build();

        final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.put(dataStoreType, NetconfIidFactory.NETCONF_TOPOLOGY_IID, topology);
        writeTransaction.commit().get(2, TimeUnit.SECONDS);
    }

    private static Node getNetconfNode(final String nodeIdent, final String ip, final int portNumber,
            final ConnectionStatus cs, final String notificationCapabilityPrefix) {

        final Host host = new Host(new IpAddress(new Ipv4Address(ip)));
        final PortNumber port = new PortNumber(Uint16.valueOf(portNumber));

        final List<AvailableCapability> avCapList = new ArrayList<>();
        avCapList.add(new AvailableCapabilityBuilder()
                .setCapabilityOrigin(AvailableCapability.CapabilityOrigin.UserDefined)
                .setCapability(notificationCapabilityPrefix + "_availableCapabilityString1").build());
        final AvailableCapabilities avCaps =
                new AvailableCapabilitiesBuilder().setAvailableCapability(avCapList).build();

        return new NodeBuilder()
                .setNodeId(new NodeId(nodeIdent))
                .addAugmentation(new NetconfNodeBuilder()
                    .setConnectionStatus(cs).setHost(host).setPort(port).setAvailableCapabilities(avCaps).build())
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

    private static void assertBaseNodeAttributesImmutableList(final Map<?, ?> mapNode) {
        assertTrue(mapNode.containsKey(NetconfConsoleConstants.NETCONF_ID));
        assertTrue(mapNode.containsKey(NetconfConsoleConstants.NETCONF_IP));
        assertTrue(mapNode.containsKey(NetconfConsoleConstants.NETCONF_PORT));
        assertTrue(mapNode.containsKey(NetconfConsoleConstants.STATUS));

        assertEquals(ImmutableList.of(NODE_ID), mapNode.get(NetconfConsoleConstants.NETCONF_ID));
        assertEquals(ImmutableList.of(IP), mapNode.get(NetconfConsoleConstants.NETCONF_IP));
        assertEquals(ImmutableList.of(String.valueOf(PORT)), mapNode.get(NetconfConsoleConstants.NETCONF_PORT));
        assertEquals(ImmutableList.of(CONN_STATUS.name()), mapNode.get(NetconfConsoleConstants.STATUS));
    }
}
