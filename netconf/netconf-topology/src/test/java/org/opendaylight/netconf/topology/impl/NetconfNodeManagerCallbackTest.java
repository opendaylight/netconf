/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import akka.testkit.JavaTestKit;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.topology.RoleChangeListener;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterMountPointDown;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.clustered.connection.status.NodeStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

public class NetconfNodeManagerCallbackTest {
    private static Config CONFIG = ConfigFactory.parseString(
            "akka {\n" +
                    "  loggers = [\"akka.testkit.TestEventListener\"]\n" +
                    "  loglevel = \"WARNING\"\n" +
                    "  stdout-loglevel = \"WARNING\"\n" +
                    " actor { provider = \"akka.cluster.ClusterActorRefProvider\"}\n" +
                    "}\n"
    );

    private ActorSystem system;

    private NetconfNode testingNode;
    private Node node;
    private NodeId nodeId;
    private NetconfNodeManagerCallback netconfNodeManagerCallback;
    private ClusteredNetconfTopology topologyDispatcher;
    private RoleChangeStrategy roleChangeStrategy;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        system = spy(ActorSystem.create("SampleActorTest", CONFIG));

        nodeId = new NodeId("nodeID");
        final NodeKey nodeKey = new NodeKey(nodeId);
        final String topologyId = "topology-id";

        topologyDispatcher = mock(ClusteredNetconfTopology.class);
        roleChangeStrategy = mock(RoleChangeStrategy.class);

        final ActorSelection actorSelection = mock(ActorSelection.class);
        final Future<ActorRef> future = mock(Future.class);

        doReturn(actorSelection).when(system).actorSelection(any(String.class));
        doReturn(future).when(actorSelection).resolveOne(any(FiniteDuration.class));
        doNothing().when(future).onComplete(any(OnComplete.class), any());

        netconfNodeManagerCallback = new NetconfNodeManagerCallback(nodeId.getValue(), topologyId, system, topologyDispatcher,roleChangeStrategy);

        testingNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(9999))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(1000L)
                .setBetweenAttemptsTimeoutMillis(100)
                .setKeepaliveDelay(1000L)
                .setTcpOnly(true)
                .setSchemaless(false)
                .setCredentials(new LoginPasswordBuilder().setUsername("testuser").setPassword("testpassword").build())
                .build();

        node = new NodeBuilder().setNodeId(nodeId).setKey(nodeKey).addAugmentation(NetconfNode.class, testingNode).build();
    }

    @After
    public void tearDown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testOnNodeCreated() throws Exception {
        doReturn(Futures.immediateFuture(new NetconfDeviceCapabilities())).when(topologyDispatcher).connectNode(any(), any());

        final ListenableFuture<Node> future = netconfNodeManagerCallback.onNodeCreated(nodeId, node);

        verify(topologyDispatcher).registerConnectionStatusListener(any(), any());
        verify(topologyDispatcher).registerNetconfClientSessionListener(any(), any());

        final Node nodeCreated = future.get(2, TimeUnit.SECONDS);
        // testing if created node contain right default parameters
        assertEquals(testingNode.getHost(), nodeCreated.getAugmentation(NetconfNode.class).getHost());
        assertEquals(testingNode.getPort(), nodeCreated.getAugmentation(NetconfNode.class).getPort());
        assertEquals(NetconfNodeConnectionStatus.ConnectionStatus.Connected, nodeCreated.getAugmentation(NetconfNode.class).getConnectionStatus());
        assertEquals(NodeStatus.Status.Connected, nodeCreated.getAugmentation(NetconfNode.class).getClusteredConnectionStatus().getNodeStatus().get(0).getStatus());
        assertEquals(true, nodeCreated.getAugmentation(NetconfNode.class).getAvailableCapabilities().getAvailableCapability().isEmpty());
        assertEquals(true, nodeCreated.getAugmentation(NetconfNode.class).getUnavailableCapabilities().getUnavailableCapability().isEmpty());
    }

    @Test
    public void testOnNodeUpdated() throws Exception {
        doReturn(Futures.immediateFuture(new NetconfDeviceCapabilities())).when(topologyDispatcher).connectNode(any(), any());

        final ListenableFuture<Node> future = netconfNodeManagerCallback.onNodeUpdated(nodeId, node);

        verify(topologyDispatcher).unregisterMountPoint(nodeId);
        verify(topologyDispatcher).disconnectNode(nodeId);
        verify(topologyDispatcher).connectNode(nodeId, node);
        verify(topologyDispatcher).registerConnectionStatusListener(any(), any());

        final Node nodeCreated = future.get(2, TimeUnit.SECONDS);
        // testing if created node contain right default parameters
        assertEquals(testingNode.getHost(), nodeCreated.getAugmentation(NetconfNode.class).getHost());
        assertEquals(testingNode.getPort(), nodeCreated.getAugmentation(NetconfNode.class).getPort());
        assertEquals(NetconfNodeConnectionStatus.ConnectionStatus.Connected, nodeCreated.getAugmentation(NetconfNode.class).getConnectionStatus());
        assertEquals(NodeStatus.Status.Connected, nodeCreated.getAugmentation(NetconfNode.class).getClusteredConnectionStatus().getNodeStatus().get(0).getStatus());
        assertEquals(true, nodeCreated.getAugmentation(NetconfNode.class).getAvailableCapabilities().getAvailableCapability().isEmpty());
        assertEquals(true, nodeCreated.getAugmentation(NetconfNode.class).getUnavailableCapabilities().getUnavailableCapability().isEmpty());
    }

    @Test
    public void testOnNodeDeleted() throws Exception {
        doReturn(Futures.immediateFuture(new NetconfDeviceCapabilities())).when(topologyDispatcher).connectNode(any(), any());

        netconfNodeManagerCallback.onNodeDeleted(nodeId);

        verify(topologyDispatcher).unregisterMountPoint(nodeId);
        verify(roleChangeStrategy).unregisterRoleCandidate();
        verify(topologyDispatcher).disconnectNode(nodeId);
    }

    @Test
    public void testOnRoleChanged() throws Exception {
        final RoleChangeListener.RoleChangeDTO roleChangeDTO = new RoleChangeListener.RoleChangeDTO(true, true, true);

        final Field f = netconfNodeManagerCallback.getClass().getDeclaredField("isMaster");
        f.setAccessible(true);

        netconfNodeManagerCallback.onRoleChanged(roleChangeDTO);

        final Object isMaster = f.get(netconfNodeManagerCallback);

        verify(topologyDispatcher).unregisterMountPoint(nodeId);
        assertEquals(roleChangeDTO.isOwner(), isMaster);
    }

    @Test
    public void testOnDeviceConnected() throws Exception {
        doReturn(Futures.immediateFuture(new NetconfDeviceCapabilities())).when(topologyDispatcher).connectNode(any(), any());
        doNothing().when(topologyDispatcher).registerMountPoint(any(akka.actor.ActorContext.class), any(NodeId.class));
        doNothing().when(topologyDispatcher).registerMountPoint(any(NodeId.class), any(ActorRef.class));

        final Field fIsMaster = netconfNodeManagerCallback.getClass().getDeclaredField("isMaster");
        fIsMaster.setAccessible(true);
        fIsMaster.set(netconfNodeManagerCallback, false); //flow without TypedActor.context()

        final Field fCurrentOperationalNode = netconfNodeManagerCallback.getClass().getDeclaredField("currentOperationalNode");
        fCurrentOperationalNode.setAccessible(true);

        final TopologyManager topologyManager = mock(TopologyManager.class);
        final Field fTopologyManager = netconfNodeManagerCallback.getClass().getDeclaredField("topologyManager");
        fTopologyManager.setAccessible(true);
        fTopologyManager.set(netconfNodeManagerCallback, topologyManager);

        doNothing().when(topologyManager).notifyNodeStatusChange(any());

        final SchemaContext remoteSchemaContext = mock(SchemaContext.class);
        final List<String> caps = Lists.newArrayList(
                "namespace:3?module=module3&revision=2012-12-12",
                "namespace:4?module=module4&revision=2012-12-12",
                "randomNonModuleCap"
        );

        final NetconfSessionPreferences netconfSessionPreferences = NetconfSessionPreferences.fromStrings(caps);

        final DOMRpcService deviceRpc = mock(DOMRpcService.class);

        netconfNodeManagerCallback.onNodeCreated(nodeId, node);
        netconfNodeManagerCallback.onDeviceConnected(remoteSchemaContext, netconfSessionPreferences, deviceRpc);

        final Node currentOperationalNode = (Node) fCurrentOperationalNode.get(netconfNodeManagerCallback);

        // testing if operational node contain right parameters after node connected
        assertEquals(testingNode.getHost(), currentOperationalNode.getAugmentation(NetconfNode.class).getHost());
        assertEquals(testingNode.getPort(), currentOperationalNode.getAugmentation(NetconfNode.class).getPort());
        assertEquals(NetconfNodeConnectionStatus.ConnectionStatus.Connected, currentOperationalNode.getAugmentation(NetconfNode.class).getConnectionStatus());
        assertEquals(NodeStatus.Status.Connected, currentOperationalNode.getAugmentation(NetconfNode.class).getClusteredConnectionStatus().getNodeStatus().get(0).getStatus());

        verify(topologyManager).notifyNodeStatusChange(any());
    }

    @Test
    public void testOnDeviceDisconnected() throws Exception {

        doReturn(Futures.immediateFuture(new NetconfDeviceCapabilities())).when(topologyDispatcher).connectNode(any(), any());

        final Field fIsMaster = netconfNodeManagerCallback.getClass().getDeclaredField("isMaster");
        fIsMaster.setAccessible(true);
        fIsMaster.set(netconfNodeManagerCallback, true);

        final Field fCurrentOperationalNode = netconfNodeManagerCallback.getClass().getDeclaredField("currentOperationalNode");
        fCurrentOperationalNode.setAccessible(true);

        final TopologyManager topologyManager = mock(TopologyManager.class);
        final Field fTopologyManager = netconfNodeManagerCallback.getClass().getDeclaredField("topologyManager");
        fTopologyManager.setAccessible(true);
        fTopologyManager.set(netconfNodeManagerCallback, topologyManager);

        doNothing().when(topologyManager).notifyNodeStatusChange(any());

        netconfNodeManagerCallback.onNodeCreated(nodeId, node);
        netconfNodeManagerCallback.onDeviceDisconnected();

        final Node currentOperationalNode = (Node) fCurrentOperationalNode.get(netconfNodeManagerCallback);
        final Object isMaster = fIsMaster.get(netconfNodeManagerCallback);

        // testing if operational node contain right parameters after device disconnected
        assertEquals(false, isMaster);
        assertEquals(testingNode.getHost(), currentOperationalNode.getAugmentation(NetconfNode.class).getHost());
        assertEquals(testingNode.getPort(), currentOperationalNode.getAugmentation(NetconfNode.class).getPort());
        assertEquals(NetconfNodeConnectionStatus.ConnectionStatus.Connecting, currentOperationalNode.getAugmentation(NetconfNode.class).getConnectionStatus());
        assertEquals(NodeStatus.Status.Unavailable, currentOperationalNode.getAugmentation(NetconfNode.class).getClusteredConnectionStatus().getNodeStatus().get(0).getStatus());
        assertEquals(true, currentOperationalNode.getAugmentation(NetconfNode.class).getAvailableCapabilities().getAvailableCapability().isEmpty());
        assertEquals(true, currentOperationalNode.getAugmentation(NetconfNode.class).getUnavailableCapabilities().getUnavailableCapability().isEmpty());

        verify(topologyManager).notifyNodeStatusChange(any());
        verify(topologyDispatcher).unregisterMountPoint(any());
    }

    @Test
    public void testOnDeviceFailed() throws Exception {
        doReturn(Futures.immediateFuture(new NetconfDeviceCapabilities())).when(topologyDispatcher).connectNode(any(), any());
        final Field fCurrentOperationalNode = netconfNodeManagerCallback.getClass().getDeclaredField("currentOperationalNode");
        fCurrentOperationalNode.setAccessible(true);

        final TopologyManager topologyManager = mock(TopologyManager.class);
        final Field fTopologyManager = netconfNodeManagerCallback.getClass().getDeclaredField("topologyManager");
        fTopologyManager.setAccessible(true);
        fTopologyManager.set(netconfNodeManagerCallback, topologyManager);

        doNothing().when(topologyManager).notifyNodeStatusChange(any());

        netconfNodeManagerCallback.onNodeCreated(nodeId, node);
        netconfNodeManagerCallback.onDeviceFailed(new Throwable("ERROR"));

        final Node currentOperationalNode = (Node) fCurrentOperationalNode.get(netconfNodeManagerCallback);

        // testing if operational node contain right parameters after device failed
        assertEquals(NetconfNodeConnectionStatus.ConnectionStatus.UnableToConnect, currentOperationalNode.getAugmentation(NetconfNode.class).getConnectionStatus());
        assertEquals(NodeStatus.Status.Failed, currentOperationalNode.getAugmentation(NetconfNode.class).getClusteredConnectionStatus().getNodeStatus().get(0).getStatus());
        assertEquals(true, currentOperationalNode.getAugmentation(NetconfNode.class).getAvailableCapabilities().getAvailableCapability().isEmpty());
        assertEquals(true, currentOperationalNode.getAugmentation(NetconfNode.class).getUnavailableCapabilities().getUnavailableCapability().isEmpty());
        assertEquals("ERROR", currentOperationalNode.getAugmentation(NetconfNode.class).getConnectedMessage());

        verify(topologyManager).notifyNodeStatusChange(any());
    }

    @Test
    public void testOnReceive() throws Exception {
        doNothing().when(topologyDispatcher).unregisterMountPoint(any());
        final AnnounceMasterMountPointDown announceMasterMountPointDown = mock(AnnounceMasterMountPointDown.class);
        final ActorRef actorRef = mock(ActorRef.class);

        netconfNodeManagerCallback.onReceive(announceMasterMountPointDown, actorRef);

        verify(topologyDispatcher).unregisterMountPoint(any());
    }

    @Test
    public void testOnSession() {
        doNothing().when(roleChangeStrategy).registerRoleCandidate(any());
        doNothing().when(roleChangeStrategy).unregisterRoleCandidate();

        final NetconfClientSession netconfClientSession = mock(NetconfClientSession.class);
        final NetconfTerminationReason netconfTerminationReason = mock(NetconfTerminationReason.class);
        final Exception exception = mock(Exception.class);
        netconfNodeManagerCallback.onSessionUp(netconfClientSession);
        netconfNodeManagerCallback.onSessionDown(netconfClientSession, exception);
        netconfNodeManagerCallback.onSessionTerminated(netconfClientSession, netconfTerminationReason);

        verify(roleChangeStrategy).registerRoleCandidate(any());
        verify(roleChangeStrategy, times(2)).unregisterRoleCandidate();

    }

}
