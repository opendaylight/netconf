/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.dom.adapter.test.ConcurrentDataBrokerTestCustomizer;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.augment.test.rev160808.Node1;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

// FIXME: base on AbstractDataBrokerTest test?
public class NetconfDeficeTopologyAdapterIntegrationTest {
    private static final RemoteDeviceId ID = new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));
    private static final KeyedInstanceIdentifier<Topology, TopologyKey> TEST_TOPOLOGY_ID =
        // FIXME: do not use this constant
        NetconfNodeUtils.DEFAULT_TOPOLOGY_IID;

    private static BindingRuntimeContext RUNTIME_CONTEXT;

    private DataBroker dataBroker;
    private DOMDataBroker domDataBroker;

    private NetconfDeviceTopologyAdapter adapter;

    @BeforeAll
    public static void beforeClass() {
        RUNTIME_CONTEXT = BindingRuntimeHelpers.createRuntimeContext(NetconfNode.class, Node1.class);
    }

    @AfterAll
    public static void afterClass() {
        RUNTIME_CONTEXT = null;
    }

    @BeforeEach
    public void setUp() throws Exception {
        final var customizer = new ConcurrentDataBrokerTestCustomizer(true);
        domDataBroker = customizer.getDOMDataBroker();
        dataBroker = customizer.createDataBroker();
        customizer.updateSchema(RUNTIME_CONTEXT);

        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, TEST_TOPOLOGY_ID, new TopologyBuilder()
            .withKey(TEST_TOPOLOGY_ID.getKey())
            .build());
        tx.commit().get(2, TimeUnit.SECONDS);

        adapter = new NetconfDeviceTopologyAdapter(dataBroker, TEST_TOPOLOGY_ID, ID, new LoginPwUnencryptedBuilder()
                .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                    .setUsername("netconf")
                    .setPassword("netconf")
                    .build())
                .build());
    }

    @Test
    public void testFailedDeviceIntegration() {
        adapter.setDeviceAsFailed(null);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> dataBroker.newReadWriteTransaction()
            .read(LogicalDatastoreType.OPERATIONAL, TEST_TOPOLOGY_ID
                .child(Node.class, new NodeKey(new NodeId(ID.name())))
                .augmentation(NetconfNode.class))
            .get(5, TimeUnit.SECONDS)
            .filter(conn -> conn.getConnectionStatus() == ConnectionStatus.UnableToConnect)
            .isPresent());
    }

    @Test
    public void testDeviceAugmentedNodePresence() throws Exception {
        final var netconfTestLeafQname = QName.create("urn:TBD:params:xml:ns:yang:network-topology-augment-test",
            "2016-08-08", "test-id").intern();
        final var loginPwUnencryptedQName = QName.create("urn:opendaylight:netconf-node-topology",
            "2023-11-21", "login-password-unencrypted").intern();
        final var credentialsQname = QName.create("urn:opendaylight:netconf-node-topology",
            "2023-11-21", "credentials").intern();
        final var usernameQName = QName.create(loginPwUnencryptedQName, "username");
        final var passwordQName = QName.create(loginPwUnencryptedQName, "password");

        final Integer dataTestId = 474747;
        final var augmentNode = ImmutableNodes.leafNode(netconfTestLeafQname, dataTestId);
        final var usernameNode = ImmutableNodes.leafNode(usernameQName, "netconf");
        final var passwordNode = ImmutableNodes.leafNode(passwordQName, "netconf");
        final var nodeIdLeaf = ImmutableNodes.leafNode(QName.create(Node.QNAME, "node-id"), "test");

        final var loginPasswordUnencryptedNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(loginPwUnencryptedQName))
            .withChild(usernameNode)
            .withChild(passwordNode)
            .build();

        final var choiceNode = ImmutableNodes.newChoiceBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(credentialsQname))
            .withChild(loginPasswordUnencryptedNode)
            .build();

        final var nodeList = ImmutableNodes.newMapEntryBuilder().withNodeIdentifier(
                YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Node.QNAME,
                    QName.create(Node.QNAME, "node-id"), "test"))
            .withChild(nodeIdLeaf)
            .withChild(choiceNode)
            .withChild(augmentNode)
            .build();

        YangInstanceIdentifier pathToNodeList = YangInstanceIdentifier.builder().node(NetworkTopology.QNAME)
            .node(Topology.QNAME)
            .nodeWithKey(Topology.QNAME, QName.create(Topology.QNAME, "topology-id"), "topology-netconf")
            .node(Node.QNAME)
            .nodeWithKey(Node.QNAME, QName.create(Node.QNAME, "node-id"), "test")
            .build();

        DOMDataTreeWriteTransaction wtx =  domDataBroker.newWriteOnlyTransaction();
        wtx.merge(LogicalDatastoreType.OPERATIONAL, pathToNodeList, nodeList);
        wtx.commit().get(5, TimeUnit.SECONDS);

        adapter.updateDeviceData(true, NetconfDeviceCapabilities.empty(), new SessionIdType(Uint32.ONE));

        //preparing expected list of nodes
        final var nodeStatusLeaf = ImmutableNodes.leafNode(QName.create("urn:opendaylight:netconf-node-topology",
            "2023-11-21", "connection-status"), "connected");
        final var nodeHostLeaf = ImmutableNodes.leafNode(QName.create("urn:opendaylight:netconf-node-topology",
            "2023-11-21", "host"), "127.0.0.1");
        final var nodePortLeaf = ImmutableNodes.leafNode(QName.create("urn:opendaylight:netconf-node-topology",
            "2023-11-21", "port"), Uint16.valueOf(22));
        final var nodeSessionIdLeaf = ImmutableNodes.leafNode(QName.create("urn:opendaylight:netconf-node-topology",
            "2023-11-21", "session-id"), Uint32.ONE);

        final var expectedData = new ArrayList<NormalizedNode>();
        expectedData.add(nodePortLeaf);
        expectedData.add(nodeStatusLeaf);
        expectedData.add(nodeHostLeaf);
        expectedData.add(nodeIdLeaf);
        expectedData.add(choiceNode);
        expectedData.add(augmentNode);
        expectedData.add(nodeSessionIdLeaf);

        final var readDataOptional = domDataBroker.newReadOnlyTransaction()
            .read(LogicalDatastoreType.OPERATIONAL, pathToNodeList)
            .get(2, TimeUnit.SECONDS)
            .map(NormalizedNode::body);

        final var actualData = new ArrayList<>((Collection) readDataOptional.orElseThrow());
        assertEquals(expectedData, actualData);

        adapter.setDeviceAsFailed(null);

        final var readDataOptionalFailedDevice = domDataBroker.newReadOnlyTransaction()
            .read(LogicalDatastoreType.OPERATIONAL, pathToNodeList)
            .get(2, TimeUnit.SECONDS)
            .map(NormalizedNode::body);
        final var actualDataFailedDevice = new ArrayList<>((Collection) readDataOptionalFailedDevice
            .orElseThrow());

        assertEquals(expectedData, actualDataFailedDevice);
    }
}
