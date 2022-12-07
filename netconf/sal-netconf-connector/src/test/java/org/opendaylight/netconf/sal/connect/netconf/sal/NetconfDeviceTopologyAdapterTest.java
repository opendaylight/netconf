/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.Transaction;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.TransactionChainListener;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.binding.dom.adapter.test.ConcurrentDataBrokerTestCustomizer;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.augment.test.rev160808.Node1;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceTopologyAdapterTest {
    private static BindingRuntimeContext RUNTIME_CONTEXT;

    private final RemoteDeviceId id = new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    @Mock
    private WriteTransaction writeTx;
    @Mock
    private TransactionChain txChain;

    private final String txIdent = "test transaction";

    private TransactionChain transactionChain;

    private DataBroker dataBroker;

    private DOMDataBroker domDataBroker;

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
        doReturn(writeTx).when(txChain).newWriteOnlyTransaction();
        doNothing().when(writeTx).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Node.class));
        doReturn(txIdent).when(writeTx).getIdentifier();

        ConcurrentDataBrokerTestCustomizer customizer = new ConcurrentDataBrokerTestCustomizer(true);
        domDataBroker = customizer.getDOMDataBroker();
        dataBroker = customizer.createDataBroker();
        customizer.updateSchema(RUNTIME_CONTEXT);

        transactionChain = dataBroker.createTransactionChain(new TransactionChainListener() {
            @Override
            public void onTransactionChainFailed(final TransactionChain chain, final Transaction transaction,
                    final Throwable cause) {

            }

            @Override
            public void onTransactionChainSuccessful(final TransactionChain chain) {

            }
        });

        final var wtx = transactionChain.newWriteOnlyTransaction();
        wtx.put(LogicalDatastoreType.OPERATIONAL, RemoteDeviceId.DEFAULT_TOPOLOGY_IID, new TopologyBuilder()
            .withKey(RemoteDeviceId.DEFAULT_TOPOLOGY_IID.getKey())
            .build());
        wtx.commit();
    }

    @Test
    public void testFailedDevice() throws Exception {

        doReturn(emptyFluentFuture()).when(writeTx).commit();
        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, txChain);
        adapter.setDeviceAsFailed(null);

        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(writeTx, times(1))
                .put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Node.class));
        adapter.close();

        adapter = new NetconfDeviceTopologyAdapter(id, transactionChain); //not a mock
        adapter.setDeviceAsFailed(null);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            Optional<NetconfNode> netconfNode = dataBroker.newReadWriteTransaction()
                    .read(LogicalDatastoreType.OPERATIONAL, id.getTopologyBindingPath().augmentation(NetconfNode.class))
                    .get(5, TimeUnit.SECONDS);
            return netconfNode.isPresent()
                && netconfNode.orElseThrow().getConnectionStatus() == ConnectionStatus.UnableToConnect;
        });
    }

    @Test
    public void testDeviceUpdate() throws Exception {
        doReturn(emptyFluentFuture()).when(writeTx).commit();

        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, txChain);
        adapter.updateDeviceData(true, new NetconfDeviceCapabilities());

        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(writeTx, times(1))
                .put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Node.class));
        verify(writeTx, times(1)).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Node.class));

    }

    @Test
    public void testDeviceAugmentedNodePresence() throws Exception {

        Integer dataTestId = 474747;

        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, transactionChain);

        QName netconfTestLeafQname = QName.create(
                "urn:TBD:params:xml:ns:yang:network-topology-augment-test", "2016-08-08", "test-id").intern();

        YangInstanceIdentifier pathToAugmentedLeaf = YangInstanceIdentifier.builder().node(NetworkTopology.QNAME)
                .node(Topology.QNAME)
                .nodeWithKey(Topology.QNAME, QName.create(Topology.QNAME, "topology-id"), "topology-netconf")
                .node(Node.QNAME)
                .nodeWithKey(Node.QNAME, QName.create(Node.QNAME, "node-id"), "test")
                .node(netconfTestLeafQname).build();

        LeafNode<Integer> augmentNode = ImmutableNodes.leafNode(netconfTestLeafQname, dataTestId);

        DOMDataTreeWriteTransaction wtx =  domDataBroker.newWriteOnlyTransaction();
        wtx.put(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf, augmentNode);
        wtx.commit().get(5, TimeUnit.SECONDS);

        adapter.updateDeviceData(true, new NetconfDeviceCapabilities());
        Optional<NormalizedNode> testNode = domDataBroker.newReadOnlyTransaction()
                .read(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf).get(2, TimeUnit.SECONDS);

        assertTrue("Augmented node data should be still present after device update.", testNode.isPresent());
        assertEquals("Augmented data should be the same as before update node.", dataTestId, testNode.get().body());

        adapter.setDeviceAsFailed(null);
        testNode = domDataBroker.newReadOnlyTransaction()
                .read(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf).get(2, TimeUnit.SECONDS);

        assertEquals("Augmented node data should be still present after device failed.", true, testNode.isPresent());
        assertEquals("Augmented data should be the same as before failed device.",
                dataTestId, testNode.get().body());
    }

    @Test
    public void testRemoveDeviceConfiguration() throws Exception {
        doReturn(emptyFluentFuture()).when(writeTx).commit();

        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, txChain);
        adapter.close();

        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(writeTx).delete(LogicalDatastoreType.OPERATIONAL, id.getTopologyBindingPath());
        verify(writeTx, times(2)).commit();
    }
}
