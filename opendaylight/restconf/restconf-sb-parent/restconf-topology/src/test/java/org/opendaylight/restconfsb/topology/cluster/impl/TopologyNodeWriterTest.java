/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.common.util.concurrent.Futures;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.ClusteredNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.ClusteredNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.clustered.connection.status.ClusteredStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.clustered.connection.status.ClusteredStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.ModuleBuilder;
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
        doReturn("transaction 1").when(wtx).getIdentifier();
        doNothing().when(wtx).merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(DataObject.class));
        doNothing().when(wtx).delete(any(LogicalDatastoreType.class), any(InstanceIdentifier.class));
        doReturn(Futures.immediateCheckedFuture(null)).when(wtx).submit();
        writer = new TopologyNodeWriter(TOPOLOGY_ID, dataBroker);
        final RestconfNode restconfNode = new RestconfNodeBuilder()
                .setModule(Collections.singletonList(new ModuleBuilder().setNamespace(new Uri("namespace")).setName(new YangIdentifier("name")).setRevision(new RevisionIdentifier("2016-02-01")).build()))
                .setStatus(org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.NodeStatus.Status.Connected)
                .build();
        final ClusteredStatus nodeStatus = new ClusteredStatusBuilder()
                .setNode("10.10.10.10")
                .setStatus(ClusteredStatus.Status.Connected)
                .build();
        final ClusteredNode clusteredNode = new ClusteredNodeBuilder()
                .setClusteredConnectionStatus(new ClusteredConnectionStatusBuilder().setClusteredStatus(Collections.singletonList(nodeStatus)).build())
                .build();
        operationalNode = new NodeBuilder()
                .setNodeId(NODE_ID)
                .addAugmentation(RestconfNode.class, restconfNode)
                .addAugmentation(ClusteredNode.class, clusteredNode)
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