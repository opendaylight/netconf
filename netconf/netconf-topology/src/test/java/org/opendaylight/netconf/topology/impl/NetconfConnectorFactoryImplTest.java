/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.HostBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NetconfConnectorFactoryImplTest {

    private static final InstanceIdentifier<Topology> TOPOLOGY_PATH = InstanceIdentifier.create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId("topology-netconf")));

    @Mock
    private
    DataBroker dataBroker;
    @Mock
    private
    WriteTransaction writeTx;
    @Captor
    private ArgumentCaptor<Throwable> callbackArgumentCaptor;

    private NodeKey nodeKey;
    private Node node;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this.getClass());
        dataBroker = mock(DataBroker.class);
        writeTx = mock(WriteTransaction.class);
        final NodeId nodeId = new NodeId("device1");
        nodeKey = new NodeKey(nodeId);
        final Credentials credentials = new LoginPasswordBuilder()
                .setUsername("admin")
                .setPassword("admin")
                .build();
        final Host host = HostBuilder.getDefaultInstance("172.17.42.1");
        final PortNumber portNumber = new PortNumber(17830);
        final NetconfNode netconfNode = new NetconfNodeBuilder()
                .setHost(host)
                .setPort(portNumber)
                .setCredentials(credentials)
                .setTcpOnly(false)
                .setReconnectOnChangedSchema(true)
                .build();
        node = new NodeBuilder()
                .setNodeId(nodeId)
                .setKey(nodeKey)
                .addAugmentation(NetconfNode.class, netconfNode)
                .build();
    }

    @Test
    public void testNewInstance() {
        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
        final InstanceIdentifier<Node> nodePath = TOPOLOGY_PATH.child(Node.class, nodeKey);
        doNothing().when(writeTx).put(LogicalDatastoreType.CONFIGURATION, nodePath, node);
        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();
        final NetconfConnectorFactoryImpl factory = new NetconfConnectorFactoryImpl();
        final Node node1 = factory.newInstance(dataBroker, "device1", "172.17.42.1", 17830,
                "admin", "admin", false, true);
        assertEquals(node, node1);
    }

    @Test
    public void testNewInstanceFail() {
        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
        final InstanceIdentifier<Node> nodePath = TOPOLOGY_PATH.child(Node.class, nodeKey);
        doNothing().when(writeTx).put(LogicalDatastoreType.CONFIGURATION, nodePath, node);
        final String message = "transaction failed";
        doReturn(Futures.immediateFailedCheckedFuture(new TransactionCommitFailedException(message, null)))
                .when(writeTx).submit();
        final NetconfConnectorFactoryImpl factory = new NetconfConnectorFactoryImpl();
        final Node node1 = factory.newInstance(dataBroker, "device1", "172.17.42.1", 17830,
                "admin", "admin", false, true);
        assertEquals(node, node1);
    }

}
