/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.AvailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.UnavailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.clustered.connection.status.NodeStatus.Status;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.clustered.connection.status.NodeStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.unavailable.capabilities.UnavailableCapability;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;

public class TopologyNodeWriterTest {

    private static final NodeId NODE_ID = new NodeId("testing-node");
    private static final String TOPOLOGY_ID = "testing-topology";

    private static final InstanceIdentifier<NetworkTopology> NETWORK_TOPOLOGY_IID = InstanceIdentifier.builder(NetworkTopology.class).build();
    private static final KeyedInstanceIdentifier<Topology, TopologyKey> TOPOLOGY_LIST_IID;

    private static final KeyedInstanceIdentifier<Node, NodeKey> OPERATIONAL_NODE_IID;

    static {
        TOPOLOGY_LIST_IID = NETWORK_TOPOLOGY_IID.child(Topology.class, new TopologyKey(new TopologyId(TOPOLOGY_ID)));
        OPERATIONAL_NODE_IID = TOPOLOGY_LIST_IID.child(Node.class, new NodeKey(new NodeId(NODE_ID)));
    }

    @Mock
    private DataBroker dataBroker;

    @Mock
    private BindingTransactionChain txChain;

    @Mock
    private WriteTransaction wtx;

    private Node operationalNode;

    private TopologyNodeWriter writer;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        doReturn(txChain).when(dataBroker).createTransactionChain(any(TransactionChainListener.class));
        doReturn(wtx).when(txChain).newWriteOnlyTransaction();
        doNothing().when(wtx).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(DataObject.class));
        doReturn(Futures.immediateCheckedFuture(null)).when(wtx).submit();
        writer = new TopologyNodeWriter(TOPOLOGY_ID, dataBroker);

        operationalNode = new NodeBuilder()
                .setNodeId(NODE_ID)
                .addAugmentation(NetconfNode.class,
                        new NetconfNodeBuilder()
                                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                                .setPort(new PortNumber(17830))
                                .setConnectionStatus(ConnectionStatus.Connected)
                                .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                .setClusteredConnectionStatus(
                                        new ClusteredConnectionStatusBuilder()
                                                .setNodeStatus(
                                                        Lists.newArrayList(
                                                                new NodeStatusBuilder()
                                                                        .setNode("10.10.10.10")
                                                                        .setStatus(Status.Connected)
                                                                        .build()))
                                                .build())
                                .build())
                .build();

    }

    @Test
    public void testInit() throws Exception {
        writer.init(NODE_ID, operationalNode);
        // once in constructor + once in init
        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(wtx, times(2)).merge(eq(LogicalDatastoreType.OPERATIONAL), eq(NETWORK_TOPOLOGY_IID), eq(new NetworkTopologyBuilder().build()));
        verify(wtx, times(2)).merge(eq(LogicalDatastoreType.OPERATIONAL), eq(TOPOLOGY_LIST_IID), eq(new TopologyBuilder().setTopologyId(new TopologyId(TOPOLOGY_ID)).build()));
        // actual write
        verify(wtx, times(1)).put(eq(LogicalDatastoreType.OPERATIONAL), eq(OPERATIONAL_NODE_IID), eq(operationalNode));

        // once in constructor + once in init()
        verify(wtx, times(2)).submit();
    }

    @Test
    public void testUpdate() throws Exception {
        writer.update(NODE_ID, operationalNode);

        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(wtx, times(1)).merge(eq(LogicalDatastoreType.OPERATIONAL), eq(NETWORK_TOPOLOGY_IID), eq(new NetworkTopologyBuilder().build()));
        verify(wtx, times(1)).merge(eq(LogicalDatastoreType.OPERATIONAL), eq(TOPOLOGY_LIST_IID), eq(new TopologyBuilder().setTopologyId(new TopologyId(TOPOLOGY_ID)).build()));
        // actual write
        verify(wtx, times(1)).put(eq(LogicalDatastoreType.OPERATIONAL), eq(OPERATIONAL_NODE_IID), eq(operationalNode));
        verify(wtx, times(2)).submit();

    }

    @Test
    public void testDelete() throws Exception {
        writer.delete(NODE_ID);
        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(wtx, times(1)).merge(eq(LogicalDatastoreType.OPERATIONAL), eq(NETWORK_TOPOLOGY_IID), eq(new NetworkTopologyBuilder().build()));
        verify(wtx, times(1)).merge(eq(LogicalDatastoreType.OPERATIONAL), eq(TOPOLOGY_LIST_IID), eq(new TopologyBuilder().setTopologyId(new TopologyId(TOPOLOGY_ID)).build()));
        verify(wtx, times(1)).delete(eq(LogicalDatastoreType.OPERATIONAL), eq(OPERATIONAL_NODE_IID));
        verify(wtx, times(2)).submit();
    }
}
