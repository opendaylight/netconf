/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.SucceededFuture;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.topology.api.SchemaRepositoryProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
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
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;

public class NetconfTopologyImplTest {

    private static final NodeId NODE_ID = new NodeId("testing-node");
    private static final String TOPOLOGY_ID = "testing-topology";

    @Mock
    private NetconfClientDispatcher mockedClientDispatcher;

    @Mock
    private EventExecutor mockedEventExecutor;

    @Mock
    private ScheduledThreadPool mockedKeepaliveExecutor;

    @Mock
    private ThreadPool mockedProcessingExecutor;

    @Mock
    private SchemaRepositoryProvider mockedSchemaRepositoryProvider;

    @Mock
    private DataBroker dataBroker;

    @Mock
    private DOMMountPointService mountPointService;

    private TestingNetconfTopologyImpl topology;
    private TestingNetconfTopologyImpl spyTopology;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mockedSchemaRepositoryProvider.getSharedSchemaRepository())
                .thenReturn(new SharedSchemaRepository("testingSharedSchemaRepo"));
        when(mockedProcessingExecutor.getExecutor()).thenReturn(MoreExecutors.newDirectExecutorService());
        final Future future = new SucceededFuture(ImmediateEventExecutor.INSTANCE, new NetconfDeviceCapabilities());
        when(mockedClientDispatcher.createReconnectingClient(any(NetconfReconnectingClientConfiguration.class)))
                .thenReturn(future);

        topology = new TestingNetconfTopologyImpl(TOPOLOGY_ID, mockedClientDispatcher,
                mockedEventExecutor, mockedKeepaliveExecutor, mockedProcessingExecutor, mockedSchemaRepositoryProvider,
                dataBroker, mountPointService);

        spyTopology = spy(topology);
    }

    @Test
    public void testInit() {
        final WriteTransaction wtx = mock(WriteTransaction.class);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(wtx);
        doNothing().when(wtx)
                .merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(DataObject.class));
        when(wtx.submit()).thenReturn(Futures.<Void, TransactionCommitFailedException>immediateCheckedFuture(null));
        topology.init();

        //verify initialization of topology
        final InstanceIdentifier<NetworkTopology> networkTopologyId =
                InstanceIdentifier.builder(NetworkTopology.class).build();
        final Topology topo = new TopologyBuilder().setTopologyId(new TopologyId(TOPOLOGY_ID)).build();
        final NetworkTopology networkTopology = new NetworkTopologyBuilder().build();
        verify(wtx).merge(LogicalDatastoreType.CONFIGURATION, networkTopologyId, networkTopology);
        verify(wtx).merge(LogicalDatastoreType.OPERATIONAL, networkTopologyId, networkTopology);
        verify(wtx).merge(LogicalDatastoreType.CONFIGURATION,
                networkTopologyId.child(Topology.class, new TopologyKey(new TopologyId(TOPOLOGY_ID))), topo);
        verify(wtx).merge(LogicalDatastoreType.OPERATIONAL,
                networkTopologyId.child(Topology.class, new TopologyKey(new TopologyId(TOPOLOGY_ID))), topo);
    }

    @Test
    public void testOnDataTreeChange() {

        final DataObjectModification<Node> newNode = mock(DataObjectModification.class);
        when(newNode.getModificationType()).thenReturn(DataObjectModification.ModificationType.WRITE);

        InstanceIdentifier.PathArgument pa = null;

        for (final InstanceIdentifier.PathArgument p
                : TopologyUtil.createTopologyListPath(TOPOLOGY_ID)
                    .child(Node.class, new NodeKey(NODE_ID)).getPathArguments()) {
            pa = p;
        }

        when(newNode.getIdentifier()).thenReturn(pa);


        final NetconfNode testingNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(9999))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(1000L)
                .setBetweenAttemptsTimeoutMillis(100)
                .setKeepaliveDelay(1000L)
                .setTcpOnly(true)
                .setCredentials(new LoginPasswordBuilder().setUsername("testuser").setPassword("testpassword").build())
                .build();

        final NodeBuilder nn = new NodeBuilder().addAugmentation(NetconfNode.class, testingNode);

        when(newNode.getDataAfter()).thenReturn(nn.build());


        final Collection<DataTreeModification<Node>> changes = Sets.newHashSet();
        final DataTreeModification<Node> ch = mock(DataTreeModification.class);
        when(ch.getRootNode()).thenReturn(newNode);
        changes.add(ch);
        spyTopology.onDataTreeChanged(changes);
        verify(spyTopology).connectNode(TopologyUtil.getNodeId(pa), nn.build());

        when(newNode.getModificationType()).thenReturn(DataObjectModification.ModificationType.DELETE);
        spyTopology.onDataTreeChanged(changes);
        verify(spyTopology).disconnectNode(TopologyUtil.getNodeId(pa));

        when(newNode.getModificationType()).thenReturn(DataObjectModification.ModificationType.SUBTREE_MODIFIED);
        spyTopology.onDataTreeChanged(changes);

        //one in previous creating and deleting node and one in updating
        verify(spyTopology, times(2)).disconnectNode(TopologyUtil.getNodeId(pa));
        verify(spyTopology, times(2)).connectNode(TopologyUtil.getNodeId(pa), nn.build());


    }

    public static class TestingNetconfTopologyImpl extends NetconfTopologyImpl {

        public TestingNetconfTopologyImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                                          final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                                          final ThreadPool processingExecutor, final SchemaRepositoryProvider schemaRepositoryProvider,
                                          final DataBroker dataBroker, final DOMMountPointService mountPointService) {
            super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor,
                    processingExecutor, schemaRepositoryProvider, dataBroker, mountPointService);
        }

        @Override
        public ListenableFuture<NetconfDeviceCapabilities> connectNode(final NodeId nodeId, final Node configNode) {
            return Futures.immediateFuture(new NetconfDeviceCapabilities());
        }

        @Override
        public ListenableFuture<Void> disconnectNode(final NodeId nodeId) {
            return Futures.immediateFuture(null);
        }
    }

}
