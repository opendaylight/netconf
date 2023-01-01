/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.Assert.assertEquals;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.dom.adapter.test.ConcurrentDataBrokerTestCustomizer;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev221225.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.augment.test.rev160808.Node1;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

// FIXME: base on AbstractDataBrokerTest test?
public class NetconfDeficeTopologyAdapterIntegrationTest {
    private static final RemoteDeviceId ID = new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    private static BindingRuntimeContext RUNTIME_CONTEXT;

    private DataBroker dataBroker;
    private DOMDataBroker domDataBroker;

    private NetconfDeviceTopologyAdapter adapter;

    @BeforeClass
    public static void beforeClass() {
        RUNTIME_CONTEXT = BindingRuntimeHelpers.createRuntimeContext(NetconfNode.class, Node1.class);
    }

    @AfterClass
    public static void afterClass() {
        RUNTIME_CONTEXT = null;
    }

    @Before
    public void setUp() throws Exception {
        final var customizer = new ConcurrentDataBrokerTestCustomizer(true);
        domDataBroker = customizer.getDOMDataBroker();
        dataBroker = customizer.createDataBroker();
        customizer.updateSchema(RUNTIME_CONTEXT);

        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, RemoteDeviceId.DEFAULT_TOPOLOGY_IID, new TopologyBuilder()
            .withKey(RemoteDeviceId.DEFAULT_TOPOLOGY_IID.getKey())
            .build());
        tx.commit().get(2, TimeUnit.SECONDS);

        adapter = new NetconfDeviceTopologyAdapter(dataBroker, ID);
    }

    @Test
    public void testFailedDeviceIntegration() {
        adapter.setDeviceAsFailed(null);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> dataBroker.newReadWriteTransaction()
            .read(LogicalDatastoreType.OPERATIONAL, ID.getTopologyBindingPath().augmentation(NetconfNode.class))
            .get(5, TimeUnit.SECONDS)
            .filter(conn -> conn.getConnectionStatus() == ConnectionStatus.UnableToConnect)
            .isPresent());
    }

    @Test
    public void testDeviceAugmentedNodePresence() throws Exception {
        QName netconfTestLeafQname = QName.create(
                "urn:TBD:params:xml:ns:yang:network-topology-augment-test", "2016-08-08", "test-id").intern();

        YangInstanceIdentifier pathToAugmentedLeaf = YangInstanceIdentifier.builder().node(NetworkTopology.QNAME)
                .node(Topology.QNAME)
                .nodeWithKey(Topology.QNAME, QName.create(Topology.QNAME, "topology-id"), "topology-netconf")
                .node(Node.QNAME)
                .nodeWithKey(Node.QNAME, QName.create(Node.QNAME, "node-id"), "test")
                .node(netconfTestLeafQname).build();

        final Integer dataTestId = 474747;
        final var augmentNode = ImmutableNodes.leafNode(netconfTestLeafQname, dataTestId);

        DOMDataTreeWriteTransaction wtx =  domDataBroker.newWriteOnlyTransaction();
        wtx.put(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf, augmentNode);
        wtx.commit().get(5, TimeUnit.SECONDS);

        adapter.updateDeviceData(true, NetconfDeviceCapabilities.empty());

        assertEquals(Optional.of(dataTestId), domDataBroker.newReadOnlyTransaction()
            .read(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf)
            .get(2, TimeUnit.SECONDS)
            .map(NormalizedNode::body));

        adapter.setDeviceAsFailed(null);

        assertEquals(Optional.of(dataTestId), domDataBroker.newReadOnlyTransaction()
            .read(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf)
            .get(2, TimeUnit.SECONDS)
            .map(NormalizedNode::body));
    }
}
