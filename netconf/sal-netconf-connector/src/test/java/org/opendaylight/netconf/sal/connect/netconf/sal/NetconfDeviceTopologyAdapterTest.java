/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.binding.test.ConcurrentDataBrokerTestCustomizer;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class NetconfDeviceTopologyAdapterTest {

    private final RemoteDeviceId id = new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    @Mock
    private WriteTransaction writeTx;
    @Mock
    private BindingTransactionChain txChain;
    @Mock
    private NetconfNode data;

    private final String txIdent = "test transaction";

    private SchemaContext schemaContext = null;
    private final String sessionIdForReporting = "netconf-test-session1";

    private BindingTransactionChain transactionChain;

    private DataBroker dataBroker;

    private DOMDataBroker domDataBroker;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(writeTx).when(txChain).newWriteOnlyTransaction();
        doNothing().when(writeTx)
                .put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(NetconfNode.class));
        doNothing().when(writeTx)
                .merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(NetconfNode.class));

        doReturn(txIdent).when(writeTx).getIdentifier();

        this.schemaContext = YangParserTestUtils.parseYangResources(NetconfDeviceTopologyAdapterTest.class,
            "/schemas/network-topology@2013-10-21.yang", "/schemas/ietf-inet-types@2013-07-15.yang",
            "/schemas/yang-ext.yang", "/schemas/netconf-node-topology.yang",
            "/schemas/network-topology-augment-test@2016-08-08.yang");
        schemaContext.getModules();

        ConcurrentDataBrokerTestCustomizer customizer = new ConcurrentDataBrokerTestCustomizer(true);
        domDataBroker = customizer.getDOMDataBroker();
        dataBroker = customizer.createDataBroker();
        customizer.updateSchema(schemaContext);

        transactionChain = dataBroker.createTransactionChain(new TransactionChainListener() {
            @Override
            public void onTransactionChainFailed(final TransactionChain<?, ?> chain,
                    final AsyncTransaction<?, ?> transaction, final Throwable cause) {

            }

            @Override
            public void onTransactionChainSuccessful(final TransactionChain<?, ?> chain) {

            }
        });

    }

    @Test
    public void testFailedDevice() throws Exception {

        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();
        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, txChain);
        adapter.setDeviceAsFailed(null);

        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(writeTx, times(1))
                .put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(NetconfNode.class));
        adapter.close();

        adapter = new NetconfDeviceTopologyAdapter(id, transactionChain); //not a mock
        adapter.setDeviceAsFailed(null);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            Optional<NetconfNode> netconfNode = dataBroker.newReadWriteTransaction()
                    .read(LogicalDatastoreType.OPERATIONAL, id.getTopologyBindingPath().augmentation(NetconfNode.class))
                    .checkedGet(5, TimeUnit.SECONDS);
            return netconfNode.isPresent() && netconfNode.get().getConnectionStatus()
                    == NetconfNodeConnectionStatus.ConnectionStatus.UnableToConnect;
        });
    }

    @Test
    public void testDeviceUpdate() throws Exception {
        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();

        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, txChain);
        adapter.updateDeviceData(true, new NetconfDeviceCapabilities());

        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(writeTx, times(1))
                .put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(NetconfNode.class));
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

        NormalizedNode<?, ?> augmentNode = ImmutableLeafNodeBuilder.create().withValue(dataTestId)
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(netconfTestLeafQname)).build();

        DOMDataWriteTransaction wtx =  domDataBroker.newWriteOnlyTransaction();
        wtx.put(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf, augmentNode);
        wtx.submit().get(5, TimeUnit.SECONDS);

        adapter.updateDeviceData(true, new NetconfDeviceCapabilities());
        Optional<NormalizedNode<?, ?>> testNode = domDataBroker.newReadOnlyTransaction()
                .read(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf).checkedGet(2, TimeUnit.SECONDS);

        assertEquals("Augmented node data should be still present after device update.", true, testNode.isPresent());
        assertEquals("Augmented data should be the same as before update node.", dataTestId, testNode.get().getValue());

        adapter.setDeviceAsFailed(null);
        testNode = domDataBroker.newReadOnlyTransaction()
                .read(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf).checkedGet(2, TimeUnit.SECONDS);

        assertEquals("Augmented node data should be still present after device failed.", true, testNode.isPresent());
        assertEquals("Augmented data should be the same as before failed device.",
                dataTestId, testNode.get().getValue());
    }

    @Test
    public void testRemoveDeviceConfiguration() throws Exception {
        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();

        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, txChain);
        adapter.close();

        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(writeTx).delete(LogicalDatastoreType.OPERATIONAL, id.getTopologyBindingPath());
        verify(writeTx, times(2)).submit();
    }

}
