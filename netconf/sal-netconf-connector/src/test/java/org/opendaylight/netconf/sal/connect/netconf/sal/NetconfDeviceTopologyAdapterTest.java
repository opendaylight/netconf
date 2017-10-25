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
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javassist.ClassPool;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.cluster.databroker.ConcurrentDOMDataBroker;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.binding.impl.BindingDOMDataBrokerAdapter;
import org.opendaylight.controller.md.sal.binding.impl.BindingToNormalizedNodeCodec;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.store.impl.InMemoryDOMDataStoreFactory;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.controller.sal.core.spi.data.DOMStore;
import org.opendaylight.mdsal.binding.generator.impl.GeneratedClassLoadingStrategy;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.generator.util.JavassistUtils;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.binding.data.codec.gen.impl.DataObjectSerializerGenerator;
import org.opendaylight.yangtools.binding.data.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class NetconfDeviceTopologyAdapterTest {

    private RemoteDeviceId id = new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    @Mock
    private DataBroker broker;
    @Mock
    private WriteTransaction writeTx;
    @Mock
    private ReadOnlyTransaction readTx;
    @Mock
    private BindingTransactionChain txChain;
    @Mock
    private NetconfNode data;

    private String txIdent = "test transaction";

    private SchemaContext schemaContext = null;
    private String sessionIdForReporting = "netconf-test-session1";

    private BindingTransactionChain transactionChain;

    private DataBroker dataBroker;

    private ConcurrentDOMDataBroker concurrentDOMDataBroker;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(txChain).when(broker).createTransactionChain(any(TransactionChainListener.class));
        doReturn(writeTx).when(txChain).newWriteOnlyTransaction();
        doNothing().when(writeTx)
                .put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(NetconfNode.class));
        doNothing().when(writeTx)
                .merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(NetconfNode.class));

        doReturn(txIdent).when(writeTx).getIdentifier();

        this.schemaContext = YangParserTestUtils.parseYangStreams(getYangSchemas());
        schemaContext.getModules();
        final SchemaService schemaService = createSchemaService();

        final DOMStore operStore = InMemoryDOMDataStoreFactory.create("DOM-OPER", schemaService);
        final DOMStore configStore = InMemoryDOMDataStoreFactory.create("DOM-CFG", schemaService);

        final EnumMap<LogicalDatastoreType, DOMStore> datastores = new EnumMap<>(LogicalDatastoreType.class);
        datastores.put(LogicalDatastoreType.CONFIGURATION, configStore);
        datastores.put(LogicalDatastoreType.OPERATIONAL, operStore);

        ExecutorService listenableFutureExecutor = SpecialExecutors.newBlockingBoundedCachedThreadPool(
                16, 16, "CommitFutures");

        concurrentDOMDataBroker = new ConcurrentDOMDataBroker(datastores, listenableFutureExecutor);

        final ClassPool pool = ClassPool.getDefault();
        final DataObjectSerializerGenerator generator = StreamWriterGenerator.create(JavassistUtils.forClassPool(pool));
        final BindingNormalizedNodeCodecRegistry codecRegistry = new BindingNormalizedNodeCodecRegistry(generator);
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        codecRegistry.onBindingRuntimeContextUpdated(
                BindingRuntimeContext.create(moduleInfoBackedContext, schemaContext));

        final GeneratedClassLoadingStrategy loading = GeneratedClassLoadingStrategy.getTCCLClassLoadingStrategy();
        final BindingToNormalizedNodeCodec bindingToNormalized =
                new BindingToNormalizedNodeCodec(loading, codecRegistry);
        bindingToNormalized.onGlobalContextUpdated(schemaContext);
        dataBroker = new BindingDOMDataBrokerAdapter(concurrentDOMDataBroker, bindingToNormalized);

        transactionChain = dataBroker.createTransactionChain(new TransactionChainListener() {
            @Override
            public void onTransactionChainFailed(TransactionChain<?, ?> chain, AsyncTransaction<?, ?> transaction,
                                                 Throwable cause) {

            }

            @Override
            public void onTransactionChainSuccessful(TransactionChain<?, ?> chain) {

            }
        });

    }

    @Test
    public void testFailedDevice() throws Exception {

        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();
        // TODO: Split up tests which test presence of previous master (both same and different one).
        when(txChain.newReadOnlyTransaction()).thenReturn(readTx);
        when(readTx.read(any(), any()))
                .thenReturn(Futures.immediateCheckedFuture(Optional.absent()));
        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, txChain);
        adapter.setDeviceAsFailed(null);

        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(writeTx, times(1))
                .put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(NetconfNode.class));
        adapter.close();

        adapter = new NetconfDeviceTopologyAdapter(id, transactionChain); //not a mock
        adapter.setDeviceAsFailed(null);

        Optional<NetconfNode> netconfNode = dataBroker.newReadWriteTransaction().read(LogicalDatastoreType.OPERATIONAL,
                id.getTopologyBindingPath().augmentation(NetconfNode.class)).checkedGet(5, TimeUnit.SECONDS);

        assertEquals("Netconf node should be presented.", true, netconfNode.isPresent());
        assertEquals("Connection status should be failed.",
                NetconfNodeConnectionStatus.ConnectionStatus.UnableToConnect.getName(),
                netconfNode.get().getConnectionStatus().getName());

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

        DOMDataWriteTransaction writeTx =  concurrentDOMDataBroker.newWriteOnlyTransaction();
        writeTx.put(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf, augmentNode);
        writeTx.submit();

        adapter.updateDeviceData(true, new NetconfDeviceCapabilities());
        Optional<NormalizedNode<?, ?>> testNode = concurrentDOMDataBroker.newReadOnlyTransaction()
                .read(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf).checkedGet(2, TimeUnit.SECONDS);

        assertEquals("Augmented node data should be still present after device update.", true, testNode.isPresent());
        assertEquals("Augmented data should be the same as before update node.", dataTestId, testNode.get().getValue());

        adapter.setDeviceAsFailed(null);
        testNode = concurrentDOMDataBroker.newReadOnlyTransaction()
                .read(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf).checkedGet(2, TimeUnit.SECONDS);

        assertEquals("Augmented node data should be still present after device failed.", true, testNode.isPresent());
        assertEquals("Augmented data should be the same as before failed device.",
                dataTestId, testNode.get().getValue());
    }

    private List<InputStream> getYangSchemas() {
        final List<String> schemaPaths = Arrays.asList("/schemas/network-topology@2013-10-21.yang",
                "/schemas/ietf-inet-types@2013-07-15.yang", "/schemas/yang-ext.yang",
                "/schemas/netconf-node-topology.yang", "/schemas/network-topology-augment-test@2016-08-08.yang");
        final List<InputStream> schemas = new ArrayList<>();

        for (String schemaPath : schemaPaths) {
            InputStream resourceAsStream = getClass().getResourceAsStream(schemaPath);
            schemas.add(resourceAsStream);
        }

        return schemas;
    }

    private SchemaService createSchemaService() {
        return new SchemaService() {

            @Override
            public void addModule(Module module) {
            }

            @Override
            public void removeModule(Module module) {

            }

            @Override
            public SchemaContext getSessionContext() {
                return schemaContext;
            }

            @Override
            public SchemaContext getGlobalContext() {
                return schemaContext;
            }

            @Override
            public ListenerRegistration<SchemaContextListener> registerSchemaContextListener(
                    final SchemaContextListener listener) {
                listener.onGlobalContextUpdated(getGlobalContext());
                return new ListenerRegistration<SchemaContextListener>() {
                    @Override
                    public void close() {

                    }

                    @Override
                    public SchemaContextListener getInstance() {
                        return listener;
                    }
                };
            }
        };
    }

    @Test
    public void testRemoveDeviceConfiguration() throws Exception {
        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();
        // TODO: Split up tests which test presence of previous master (both same and different one).
        when(txChain.newReadOnlyTransaction()).thenReturn(readTx);
        when(readTx.read(any(), any()))
                .thenReturn(Futures.immediateCheckedFuture(Optional.absent()));

        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, txChain);
        adapter.close();

        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(writeTx).delete(LogicalDatastoreType.OPERATIONAL, id.getTopologyBindingPath());
        verify(writeTx, times(2)).submit();
    }

}
