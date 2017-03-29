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
import static org.mockito.Matchers.refEq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.TypedActor;
import akka.actor.TypedActor$;
import akka.actor.TypedActorExtension;
import akka.actor.TypedProps;
import akka.cluster.Cluster;
import akka.cluster.Cluster$;
import akka.dispatch.OnComplete;
import akka.util.Timeout;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opendaylight.netconf.topology.NodeListener;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.RoleChangeListener;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterMountPoint;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterMountPointDown;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.ClusteredNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.clustered.connection.status.ClusteredStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.NodeStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

@RunWith(PowerMockRunner.class)
@PrepareForTest(TypedActor.class)
public class RestconfNodeManagerCallbackTest {

    private static final Address ADDRESS = Address.apply("akka", "user", "172.17.0.1", 5555);
    private static final List<Module> modules = new ArrayList<>();
    private static final NodeId NODE_ID = new NodeId("node1");
    private NodeManagerCallback callback;
    @Mock
    private ActorSystem actorSystem;
    @Mock
    private ClusteredRestconfTopology topology;
    @Mock
    private RoleChangeStrategy roleChangeStrategy;
    @Mock
    private Cluster cluster;
    @Mock
    private TypedActorExtension typedActorExtension;
    @Mock
    private ActorRef nodeManagerRef;
    @Mock
    private NodeManager nodeManager;
    @Mock
    private ActorRef topologyManagerRef;
    @Mock
    private TopologyManager topologyManager;

    @BeforeClass
    public static void suiteSetup() {
        modules.add(new ModuleBuilder().setNamespace(new Uri("ns1")).setName(new YangIdentifier("name1")).setRevision(new RevisionIdentifier("2016-02-01")).build());
        modules.add(new ModuleBuilder().setNamespace(new Uri("ns2")).setName(new YangIdentifier("name2")).setRevision(new RevisionIdentifier("2016-03-02")).build());
        modules.add(new ModuleBuilder().setNamespace(new Uri("ns3")).setName(new YangIdentifier("name3")).setRevision(new RevisionIdentifier("2016-04-03")).build());
    }

    @Before
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);
        doReturn(typedActorExtension).when(actorSystem).registerExtension(TypedActor$.MODULE$);

        doNothing().when(topologyManager).notifyNodeStatusChange(NODE_ID);
        doReturn(cluster).when(actorSystem).registerExtension(Cluster$.MODULE$);
        doReturn(ADDRESS).when(cluster).selfAddress();
        final ExecutionContextExecutor executionContextExecutor = mock(ExecutionContextExecutor.class);
        doReturn("bla").when(executionContextExecutor).toString();
        doReturn(executionContextExecutor).when(actorSystem).dispatcher();

        final ActorSelection topologyManagerSelection = mock(ActorSelection.class);
        doReturn(topologyManagerSelection).when(actorSystem).actorSelection("/user/topology-restconf");
        final Future topologyManagerFuture = mock(Future.class);
        doNothing().when(topologyManagerFuture).onComplete(any(OnComplete.class), any(ExecutionContext.class));
        doReturn(topologyManagerFuture).when(topologyManagerSelection).resolveOne((FiniteDuration) any());

        final ActorSelection nodeManagerSelection = mock(ActorSelection.class);
        doReturn(nodeManagerSelection).when(actorSystem).actorSelection("/user/topology-restconf/node1");
        final Future nodeManagerFuture = mock(Future.class);
        doNothing().when(nodeManagerFuture).onComplete(any(OnComplete.class), any(ExecutionContext.class));
        doReturn(nodeManagerFuture).when(nodeManagerSelection).resolveOne((FiniteDuration) any());

        initTopologyMock();

        doNothing().when(roleChangeStrategy).registerRoleCandidate(any(NodeListener.class));
        doNothing().when(roleChangeStrategy).unregisterRoleCandidate();


        PowerMockito.mockStatic(TypedActor.class, new Answer() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                return null;
            }
        });
        when(TypedActor.context()).thenReturn(null);
        when(TypedActor.get(actorSystem)).thenReturn(typedActorExtension);
        doReturn(nodeManager).when(typedActorExtension).typedActorOf(any(TypedProps.class), same(nodeManagerRef));
        doReturn(topologyManager).when(typedActorExtension).typedActorOf(any(TypedProps.class), same(topologyManagerRef));
        callback = new RestconfNodeManagerCallback("node1", "topology-restconf", actorSystem, topology, roleChangeStrategy);
        completeActorSelectionFutures(topologyManagerFuture, nodeManagerFuture);

    }

    private void completeActorSelectionFutures(final Future topologyManagerFuture, final Future nodeManagerFuture) {
        final ArgumentCaptor<OnComplete> topologyCaptor = ArgumentCaptor.forClass(OnComplete.class);
        verify(topologyManagerFuture).onComplete(topologyCaptor.capture(), any(ExecutionContext.class));
        try {
            topologyCaptor.getValue().onComplete(null, topologyManagerRef);
        } catch (final Throwable throwable) {
            throwable.printStackTrace();
        }
        final ArgumentCaptor<OnComplete> nodeCaptor = ArgumentCaptor.forClass(OnComplete.class);
        verify(nodeManagerFuture).onComplete(nodeCaptor.capture(), any(ExecutionContext.class));
        try {
            nodeCaptor.getValue().onComplete(null, nodeManagerRef);
        } catch (final Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private void initTopologyMock() {
        doReturn(Futures.immediateFuture(modules)).when(topology).connectNode(any(NodeId.class), any(Node.class));
        doReturn(new Timeout(5, TimeUnit.SECONDS)).when(topology).getAskTimeout();
        doNothing().when(topology).unregisterMountPoint(NODE_ID);
        doNothing().when(topology).registerMasterMountPoint(any(ActorContext.class), eq(NODE_ID));
        doNothing().when(topology).registerSlaveMountPoint(any(ActorContext.class), eq(NODE_ID), any(ActorRef.class));
        doReturn(Futures.immediateFuture(null)).when(topology).disconnectNode(NODE_ID);
    }

    @Test
    public void testGetInitialState() throws Exception {
        final Node node = callback.getInitialState(new NodeId("node1"), null);
        final RestconfNode restconfNode = node.getAugmentation(RestconfNode.class);
        final ClusteredNode clusteredNode = node.getAugmentation(ClusteredNode.class);
        Assert.assertNull(restconfNode.getModule());
        Assert.assertEquals(NodeStatus.Status.Connecting, restconfNode.getStatus());
        final List<ClusteredStatus> actualStatus = clusteredNode.getClusteredConnectionStatus().getClusteredStatus();
        Assert.assertEquals(1, actualStatus.size());
        Assert.assertEquals(ADDRESS.toString(), actualStatus.get(0).getNode());
        Assert.assertEquals(ClusteredStatus.Status.Unavailable, actualStatus.get(0).getStatus());
    }

    @Test
    public void testGetFailedState() throws Exception {
        final Node node = callback.getFailedState(new NodeId("node1"), null);
        final RestconfNode restconfNode = node.getAugmentation(RestconfNode.class);
        final ClusteredNode clusteredNode = node.getAugmentation(ClusteredNode.class);
        Assert.assertNull(restconfNode.getModule());
        Assert.assertEquals(NodeStatus.Status.Failed, restconfNode.getStatus());
        final List<ClusteredStatus> actualStatus = clusteredNode.getClusteredConnectionStatus().getClusteredStatus();
        Assert.assertEquals(1, actualStatus.size());
        Assert.assertEquals(ADDRESS.toString(), actualStatus.get(0).getNode());
        Assert.assertEquals(ClusteredStatus.Status.Failed, actualStatus.get(0).getStatus());
    }

    @Test
    public void testOnNodeCreated() throws Exception {
        final ListenableFuture<Node> nodeFuture = callback.onNodeCreated(new NodeId("node1"), null);
        Assert.assertTrue(nodeFuture.isDone());
        final Node node = nodeFuture.get();
        final RestconfNode restconfNode = node.getAugmentation(RestconfNode.class);
        final ClusteredNode clusteredNode = node.getAugmentation(ClusteredNode.class);
        Assert.assertEquals(modules, restconfNode.getModule());
        Assert.assertEquals(NodeStatus.Status.Connected, restconfNode.getStatus());
        final List<ClusteredStatus> actualStatus = clusteredNode.getClusteredConnectionStatus().getClusteredStatus();
        Assert.assertEquals(1, actualStatus.size());
        Assert.assertEquals(ADDRESS.toString(), actualStatus.get(0).getNode());
        Assert.assertEquals(ClusteredStatus.Status.Connected, actualStatus.get(0).getStatus());
        verify(roleChangeStrategy, timeout(5000)).registerRoleCandidate(nodeManager);
    }

    @Test
    public void testOnNodeUpdated() throws Exception {
        final ListenableFuture<Node> nodeFuture = callback.onNodeUpdated(NODE_ID, null);
        Assert.assertTrue(nodeFuture.isDone());
        final Node node = nodeFuture.get();
        final RestconfNode restconfNode = node.getAugmentation(RestconfNode.class);
        final ClusteredNode clusteredNode = node.getAugmentation(ClusteredNode.class);
        verify(roleChangeStrategy).unregisterRoleCandidate();
        verify(topologyManager).notifyNodeStatusChange(NODE_ID);
        Assert.assertEquals(modules, restconfNode.getModule());
        Assert.assertEquals(NodeStatus.Status.Connected, restconfNode.getStatus());
        final List<ClusteredStatus> actualStatus = clusteredNode.getClusteredConnectionStatus().getClusteredStatus();
        Assert.assertEquals(1, actualStatus.size());
        Assert.assertEquals(ADDRESS.toString(), actualStatus.get(0).getNode());
        Assert.assertEquals(ClusteredStatus.Status.Connected, actualStatus.get(0).getStatus());
        verify(roleChangeStrategy, timeout(5000)).registerRoleCandidate(nodeManager);
    }

    @Test
    public void testOnNodeDeletedSlave() throws Exception {
        callback.onNodeDeleted(NODE_ID);
        verify(roleChangeStrategy).unregisterRoleCandidate();
        verify(topologyManager).notifyNodeStatusChange(NODE_ID);
    }

    @Test
    public void testOnNodeDeletedMaster() throws Exception {
        callback.onNodeCreated(NODE_ID, null);
        callback.onRoleChanged(new RoleChangeListener.RoleChangeDTO(false, true, false));
        callback.onNodeDeleted(NODE_ID);
        verify(roleChangeStrategy).unregisterRoleCandidate();
        verify(topologyManager, times(2)).notifyNodeStatusChange(NODE_ID);
        verify(topology, times(2)).unregisterMountPoint(NODE_ID);
    }

    @Test
    public void testGetCurrentStatusForNode() throws Exception {
        final ListenableFuture<Node> nodeListenableFuture = callback.onNodeCreated(NODE_ID, null);
        final Node created = nodeListenableFuture.get();
        final Node current = callback.getCurrentStatusForNode(NODE_ID).get();
        Assert.assertEquals(created, current);
        final ListenableFuture<Void> deleted = callback.onNodeDeleted(NODE_ID);
        deleted.get();
        final Node currentAfterDelete = callback.getCurrentStatusForNode(NODE_ID).get();
        final List<ClusteredStatus> clusteredStatus = currentAfterDelete.getAugmentation(ClusteredNode.class).getClusteredConnectionStatus().getClusteredStatus();
        Assert.assertEquals(1, clusteredStatus.size());
        Assert.assertEquals(ClusteredStatus.Status.Unavailable, clusteredStatus.get(0).getStatus());
    }

    @Test
    public void testOnReceiveMasterMountpointDown() throws Exception {
        final ActorRef sender = mock(ActorRef.class);
        callback.onReceive(new AnnounceMasterMountPointDown(), sender);
        verify(topology, timeout(5000)).unregisterMountPoint(NODE_ID);
    }

    @Test
    public void testOnReceiveMasterMountpointUp() throws Exception {
        final ActorRef sender = mock(ActorRef.class);
        callback.onReceive(new AnnounceMasterMountPoint(), sender);
        verify(topology).registerSlaveMountPoint((ActorContext) eq(null), eq(NODE_ID), refEq(sender));
    }

    @Test
    public void testOnRoleChanged() throws Exception {
        callback.onNodeCreated(NODE_ID, null);
        callback.onRoleChanged(new RoleChangeListener.RoleChangeDTO(false, true, false));
        verify(topology, timeout(3000)).unregisterMountPoint(new NodeId("node1"));
        verify(topology).registerMasterMountPoint(null, NODE_ID);
    }
}