/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.typesafe.config.ConfigFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.SucceededFuture;
import java.io.File;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.cluster.ActorSystemProvider;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.binding.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcIdentifier;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcImplementation;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.api.DOMService;
import org.opendaylight.controller.md.sal.dom.broker.impl.DOMRpcRouter;
import org.opendaylight.controller.md.sal.dom.broker.impl.mount.DOMMountPointServiceImpl;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.controller.sal.core.api.mount.MountProvisionListener;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.eos.dom.simple.SimpleDOMEntityOwnershipService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceRegistration;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.mdsal.singleton.dom.impl.DOMClusterSingletonServiceProviderImpl;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringRpcException;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.Networks;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NodeId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.Network;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.Node;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.NetconfNodeConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.netconf.node.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.netconf.node.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.networks.network.network.types.NetconfNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.topology.singleton.config.rev170419.Config;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.topology.singleton.config.rev170419.ConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.GetTopOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.PutTopInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.Top;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.two.level.list.TopLevelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.two.level.list.TopLevelListBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.binding.util.BindingReflections;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests netconf mount points end-to-end.
 *
 * @author Thomas Pantelis
 */
public class MountPointEndToEndTest {
    private static Logger LOG = LoggerFactory.getLogger(MountPointEndToEndTest.class);

    private static final String TOP_MODULE_NAME = "opendaylight-mdsal-list-test";
    private static final String ACTOR_SYSTEM_NAME = "test";
    private static final String TOPOLOGY_ID = NetconfNetwork.QNAME.getLocalName();
    private static final NodeId NODE_ID = new NodeId("node-id");
    private static final InstanceIdentifier<Node> NODE_INSTANCE_ID = NetconfTopologyUtils.createTopologyNodeListPath(
            new NodeKey(NODE_ID), TOPOLOGY_ID);

    @Mock private RpcProviderRegistry mockRpcProviderRegistry;
    @Mock private NetconfClientDispatcher mockClientDispatcher;
    @Mock private AAAEncryptionService mockEncryptionService;
    @Mock private ThreadPool mockThreadPool;
    @Mock private ScheduledThreadPool mockKeepaliveExecutor;

    @Mock private ActorSystemProvider mockMasterActorSystemProvider;
    @Mock private MountProvisionListener masterMountPointListener;
    private final DOMMountPointService masterMountPointService = new DOMMountPointServiceImpl();
    private final DOMRpcRouter deviceRpcService = new DOMRpcRouter();
    private DOMClusterSingletonServiceProviderImpl masterClusterSingletonServiceProvider;
    private DataBroker masterDataBroker;
    private DOMDataBroker deviceDOMDataBroker;
    private ActorSystem masterSystem;
    private NetconfTopologyManager masterNetconfTopologyManager;
    private volatile SettableFuture<MasterSalFacade> masterSalFacadeFuture = SettableFuture.create();

    @Mock private ActorSystemProvider mockSlaveActorSystemProvider;
    @Mock private ClusterSingletonServiceProvider mockSlaveClusterSingletonServiceProvider;
    @Mock private ClusterSingletonServiceRegistration mockSlaveClusterSingletonServiceReg;
    @Mock private MountProvisionListener slaveMountPointListener;
    private final DOMMountPointService slaveMountPointService = new DOMMountPointServiceImpl();
    private DataBroker slaveDataBroker;
    private ActorSystem slaveSystem;
    private NetconfTopologyManager slaveNetconfTopologyManager;
    private final SettableFuture<NetconfTopologyContext> slaveNetconfTopologyContextFuture = SettableFuture.create();
    private BindingTransactionChain slaveTxChain;

    private final EventExecutor eventExecutor = GlobalEventExecutor.INSTANCE;
    private final Config config = new ConfigBuilder().setWriteTransactionIdleTimeout(0).build();
    private SchemaContext deviceSchemaContext;
    private YangModuleInfo topModuleInfo;
    private SchemaPath putTopRpcSchemaPath;
    private SchemaPath getTopRpcSchemaPath;
    private BindingNormalizedNodeSerializer bindingToNormalized;
    private YangInstanceIdentifier yangNodeInstanceId;
    private final TopDOMRpcImplementation topRpcImplementation = new TopDOMRpcImplementation();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Before
    public void setUp() throws Exception {
        initMocks(this);

        deleteCacheDir();

        topModuleInfo = BindingReflections.getModuleInfo(Top.class);

        final ModuleInfoBackedContext moduleContext = ModuleInfoBackedContext.create();
        moduleContext.addModuleInfos(Arrays.asList(topModuleInfo));
        deviceSchemaContext = moduleContext.tryToCreateSchemaContext().get();

        deviceRpcService.onGlobalContextUpdated(deviceSchemaContext);

        putTopRpcSchemaPath = findRpcDefinition("put-top").getPath();
        getTopRpcSchemaPath = findRpcDefinition("get-top").getPath();

        deviceRpcService.registerRpcImplementation(topRpcImplementation,
                DOMRpcIdentifier.create(putTopRpcSchemaPath), DOMRpcIdentifier.create(getTopRpcSchemaPath));

        setupMaster();

        setupSlave();

        yangNodeInstanceId = bindingToNormalized.toYangInstanceIdentifier(NODE_INSTANCE_ID);

        doReturn(new SucceededFuture(GlobalEventExecutor.INSTANCE, null)).when(mockClientDispatcher)
                .createReconnectingClient(any());

        LOG.info("****** Setup complete");
    }

    private void deleteCacheDir() {
        FileUtils.deleteQuietly(new File(NetconfTopologyUtils.CACHE_DIRECTORY));
    }

    @After
    public void tearDown() throws Exception {
        deleteCacheDir();
        TestKit.shutdownActorSystem(slaveSystem, Boolean.TRUE);
        TestKit.shutdownActorSystem(masterSystem, Boolean.TRUE);
    }

    private void setupMaster() throws Exception {
        AbstractConcurrentDataBrokerTest dataBrokerTest = newDataBrokerTest();
        masterDataBroker = dataBrokerTest.getDataBroker();
        deviceDOMDataBroker = dataBrokerTest.getDomBroker();
        bindingToNormalized = dataBrokerTest.getDataBrokerTestCustomizer().getBindingToNormalized();

        masterSystem = ActorSystem.create(ACTOR_SYSTEM_NAME, ConfigFactory.load().getConfig("Master"));

        masterClusterSingletonServiceProvider = new DOMClusterSingletonServiceProviderImpl(
                new SimpleDOMEntityOwnershipService());
        masterClusterSingletonServiceProvider.initializeProvider();

        doReturn(masterSystem).when(mockMasterActorSystemProvider).getActorSystem();

        doReturn(MoreExecutors.newDirectExecutorService()).when(mockThreadPool).getExecutor();

        NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY.registerSchemaSource(
            id -> Futures.immediateFuture(YangTextSchemaSource.delegateForByteSource(id,
                    topModuleInfo.getYangTextByteSource())),
            PotentialSchemaSource.create(RevisionSourceIdentifier.create(TOP_MODULE_NAME,
                    topModuleInfo.getName().getRevision()), YangTextSchemaSource.class, 1));

        masterNetconfTopologyManager = new NetconfTopologyManager(masterDataBroker, mockRpcProviderRegistry,
                masterClusterSingletonServiceProvider, mockKeepaliveExecutor, mockThreadPool,
                mockMasterActorSystemProvider, eventExecutor, mockClientDispatcher, TOPOLOGY_ID, config,
                masterMountPointService, mockEncryptionService) {
            @Override
            protected NetconfTopologyContext newNetconfTopologyContext(final NetconfTopologySetup setup,
                    final ServiceGroupIdentifier serviceGroupIdent, final Timeout actorResponseWaitTime) {
                NetconfTopologyContext context =
                        super.newNetconfTopologyContext(setup, serviceGroupIdent, actorResponseWaitTime);
                NetconfTopologyContext spiedContext = spy(context);
                doAnswer(invocation -> {
                    final MasterSalFacade spiedFacade = (MasterSalFacade) spy(invocation.callRealMethod());
                    doReturn(deviceDOMDataBroker).when(spiedFacade).newDeviceDataBroker();
                    masterSalFacadeFuture.set(spiedFacade);
                    return spiedFacade;
                }).when(spiedContext).newMasterSalFacade();

                return spiedContext;
            }
        };

        masterNetconfTopologyManager.init();

        verifyTopologyNodesCreated(masterDataBroker);
    }

    private void setupSlave() throws Exception {
        AbstractConcurrentDataBrokerTest dataBrokerTest = newDataBrokerTest();
        slaveDataBroker = dataBrokerTest.getDataBroker();

        slaveSystem = ActorSystem.create(ACTOR_SYSTEM_NAME, ConfigFactory.load().getConfig("Slave"));

        doReturn(slaveSystem).when(mockSlaveActorSystemProvider).getActorSystem();

        doReturn(mockSlaveClusterSingletonServiceReg).when(mockSlaveClusterSingletonServiceProvider)
                .registerClusterSingletonService(any());

        slaveNetconfTopologyManager = new NetconfTopologyManager(slaveDataBroker, mockRpcProviderRegistry,
                mockSlaveClusterSingletonServiceProvider, mockKeepaliveExecutor, mockThreadPool,
                mockSlaveActorSystemProvider, eventExecutor, mockClientDispatcher, TOPOLOGY_ID, config,
                slaveMountPointService, mockEncryptionService)  {
            @Override
            protected NetconfTopologyContext newNetconfTopologyContext(final NetconfTopologySetup setup,
                    final ServiceGroupIdentifier serviceGroupIdent, final Timeout actorResponseWaitTime) {
                NetconfTopologyContext spiedContext =
                        spy(super.newNetconfTopologyContext(setup, serviceGroupIdent, actorResponseWaitTime));
                slaveNetconfTopologyContextFuture.set(spiedContext);
                return spiedContext;
            }
        };

        slaveNetconfTopologyManager.init();

        verifyTopologyNodesCreated(slaveDataBroker);

        slaveTxChain = slaveDataBroker.createTransactionChain(new TransactionChainListener() {
            @Override
            public void onTransactionChainSuccessful(final TransactionChain<?, ?> chain) {
            }

            @Override
            public void onTransactionChainFailed(final TransactionChain<?, ?> chain,
                    final AsyncTransaction<?, ?> transaction, final Throwable cause) {
                LOG.error("Slave transaction chain failed", cause);
            }
        });
    }

    @Test
    public void test() throws Exception {
        testMaster();

        testSlave();

        final MasterSalFacade masterSalFacade = testMasterNodeUpdated();

        testMasterDisconnected(masterSalFacade);

        testCleanup();
    }

    private MasterSalFacade testMaster() throws InterruptedException, ExecutionException, TimeoutException {
        LOG.info("****** Testing master");

        writeNetconfNode(NetconfTopologyUtils.DEFAULT_CACHE_DIRECTORY, masterDataBroker);

        final MasterSalFacade masterSalFacade = masterSalFacadeFuture.get(5, TimeUnit.SECONDS);

        masterSalFacade.onDeviceConnected(deviceSchemaContext,
                NetconfSessionPreferences.fromStrings(Collections.emptyList()), deviceRpcService);

        DOMMountPoint masterMountPoint = awaitMountPoint(masterMountPointService);

        LOG.info("****** Testing master DOMDataBroker operations");

        testDOMDataBrokerOperations(getDOMDataBroker(masterMountPoint));

        LOG.info("****** Testing master DOMRpcService");

        testDOMRpcService(getDOMRpcService(masterMountPoint));
        return masterSalFacade;
    }

    private void testSlave() throws InterruptedException, ExecutionException, TimeoutException {
        LOG.info("****** Testing slave");

        writeNetconfNode("slave", slaveDataBroker);

        verify(mockSlaveClusterSingletonServiceProvider, timeout(5000)).registerClusterSingletonService(any());

        // Since the master and slave use separate DataBrokers we need to copy the master's oper node to the slave.
        // This is essentially what happens in a clustered environment but we'll use a DTCL here.

        masterDataBroker.registerDataTreeChangeListener(
            new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL, NODE_INSTANCE_ID), changes -> {
                final WriteTransaction slaveTx = slaveTxChain.newWriteOnlyTransaction();
                for (DataTreeModification<Node> dataTreeModification : changes) {
                    DataObjectModification<Node> rootNode = dataTreeModification.getRootNode();
                    InstanceIdentifier<Node> path = dataTreeModification.getRootPath().getRootIdentifier();
                    switch (rootNode.getModificationType()) {
                        case WRITE:
                        case SUBTREE_MODIFIED:
                            slaveTx.merge(LogicalDatastoreType.OPERATIONAL, path, rootNode.getDataAfter());
                            break;
                        case DELETE:
                            slaveTx.delete(LogicalDatastoreType.OPERATIONAL, path);
                            break;
                        default:
                            break;
                    }
                }

                slaveTx.commit();
            });

        DOMMountPoint slaveMountPoint = awaitMountPoint(slaveMountPointService);

        final NetconfTopologyContext slaveNetconfTopologyContext =
                slaveNetconfTopologyContextFuture.get(5, TimeUnit.SECONDS);
        verify(slaveNetconfTopologyContext, never()).newMasterSalFacade();

        LOG.info("****** Testing slave DOMDataBroker operations");

        testDOMDataBrokerOperations(getDOMDataBroker(slaveMountPoint));

        LOG.info("****** Testing slave DOMRpcService");

        testDOMRpcService(getDOMRpcService(slaveMountPoint));
    }

    private MasterSalFacade testMasterNodeUpdated() throws InterruptedException, ExecutionException, TimeoutException {
        LOG.info("****** Testing update master node");

        masterMountPointService.registerProvisionListener(masterMountPointListener);
        slaveMountPointService.registerProvisionListener(slaveMountPointListener);

        masterSalFacadeFuture = SettableFuture.create();
        writeNetconfNode(NetconfTopologyUtils.DEFAULT_CACHE_DIRECTORY, masterDataBroker);

        verify(masterMountPointListener, timeout(5000)).onMountPointRemoved(yangNodeInstanceId);

        MasterSalFacade masterSalFacade = masterSalFacadeFuture.get(5, TimeUnit.SECONDS);

        masterSalFacade.onDeviceConnected(deviceSchemaContext,
                NetconfSessionPreferences.fromStrings(Collections.emptyList()), deviceRpcService);

        verify(masterMountPointListener, timeout(5000)).onMountPointCreated(yangNodeInstanceId);

        verify(slaveMountPointListener, timeout(5000)).onMountPointRemoved(yangNodeInstanceId);
        verify(slaveMountPointListener, timeout(5000)).onMountPointCreated(yangNodeInstanceId);

        return masterSalFacade;
    }

    private void testMasterDisconnected(final MasterSalFacade masterSalFacade)
            throws InterruptedException, ExecutionException, TimeoutException {
        LOG.info("****** Testing master disconnected");

        masterSalFacade.onDeviceDisconnected();

        awaitMountPointNotPresent(masterMountPointService);

        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            try (ReadOnlyTransaction readTx = masterDataBroker.newReadOnlyTransaction()) {
                Optional<Node> node = readTx.read(LogicalDatastoreType.OPERATIONAL,
                        NODE_INSTANCE_ID).get(5, TimeUnit.SECONDS);
                assertTrue(node.isPresent());
                final NetconfNode netconfNode = node.get().augmentation(NetconfNode.class);
                return netconfNode.getConnectionStatus() != NetconfNodeConnectionStatus.ConnectionStatus.Connected;
            }
        });

        awaitMountPointNotPresent(slaveMountPointService);
    }

    private void testCleanup() throws Exception {
        LOG.info("****** Testing cleanup");

        slaveNetconfTopologyManager.close();
        verify(mockSlaveClusterSingletonServiceReg).close();
    }

    private void testDOMRpcService(final DOMRpcService domRpcService)
            throws InterruptedException, ExecutionException, TimeoutException {
        testPutTopRpc(domRpcService, new DefaultDOMRpcResult((NormalizedNode<?, ?>)null));
        testPutTopRpc(domRpcService, null);
        testPutTopRpc(domRpcService, new DefaultDOMRpcResult(ImmutableList.of(
                RpcResultBuilder.newError(ErrorType.APPLICATION, "tag1", "error1"),
                RpcResultBuilder.newError(ErrorType.APPLICATION, "tag2", "error2"))));

        testGetTopRpc(domRpcService, new DefaultDOMRpcResult(bindingToNormalized.toNormalizedNodeRpcData(
                new GetTopOutputBuilder().setTopLevelList(Arrays.asList(new TopLevelListBuilder().setName("one")
                        .build())).build())));

        testFailedRpc(domRpcService, getTopRpcSchemaPath, null);
    }

    private void testPutTopRpc(final DOMRpcService domRpcService, final DOMRpcResult result)
            throws InterruptedException, ExecutionException, TimeoutException {
        ContainerNode putTopInput = bindingToNormalized.toNormalizedNodeRpcData(
                new PutTopInputBuilder().setTopLevelList(Arrays.asList(new TopLevelListBuilder().setName("one")
                        .build())).build());
        testRpc(domRpcService, putTopRpcSchemaPath, putTopInput, result);
    }

    private void testGetTopRpc(final DOMRpcService domRpcService, final DOMRpcResult result)
            throws InterruptedException, ExecutionException, TimeoutException {
        testRpc(domRpcService, getTopRpcSchemaPath, null, result);
    }

    private void testRpc(final DOMRpcService domRpcService, final SchemaPath schemaPath,
            final NormalizedNode<?, ?> input, final DOMRpcResult result)
            throws InterruptedException, ExecutionException, TimeoutException {
        final DOMRpcResult actual = invokeRpc(domRpcService, schemaPath, input, Futures.immediateCheckedFuture(result));
        if (result == null) {
            assertNull(actual);
            return;
        }

        assertNotNull(actual);
        assertEquals(result.getResult(), actual.getResult());

        assertEquals(result.getErrors().size(), actual.getErrors().size());
        Iterator<RpcError> iter1 = result.getErrors().iterator();
        Iterator<RpcError> iter2 = actual.getErrors().iterator();
        while (iter1.hasNext() && iter2.hasNext()) {
            RpcError err1 = iter1.next();
            RpcError err2 = iter2.next();
            assertEquals(err1.getErrorType(), err2.getErrorType());
            assertEquals(err1.getTag(), err2.getTag());
            assertEquals(err1.getMessage(), err2.getMessage());
            assertEquals(err1.getSeverity(), err2.getSeverity());
            assertEquals(err1.getApplicationTag(), err2.getApplicationTag());
            assertEquals(err1.getInfo(), err2.getInfo());
        }
    }

    private void testFailedRpc(final DOMRpcService domRpcService, final SchemaPath schemaPath,
            final NormalizedNode<?, ?> input) throws InterruptedException, TimeoutException {
        try {
            invokeRpc(domRpcService, schemaPath, input, Futures.immediateFailedCheckedFuture(
                    new ClusteringRpcException("mock")));
            fail("Expected exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ClusteringRpcException);
            assertEquals("mock", e.getCause().getMessage());
        }
    }

    private DOMRpcResult invokeRpc(final DOMRpcService domRpcService, final SchemaPath schemaPath,
            final NormalizedNode<?, ?> input, final CheckedFuture<DOMRpcResult, DOMRpcException> returnFuture)
            throws InterruptedException, ExecutionException, TimeoutException {
        topRpcImplementation.init(returnFuture);
        final ListenableFuture<DOMRpcResult> resultFuture = domRpcService.invokeRpc(schemaPath, input);

        topRpcImplementation.verify(DOMRpcIdentifier.create(schemaPath), input);

        return resultFuture.get(5, TimeUnit.SECONDS);
    }

    private static void testDOMDataBrokerOperations(final DOMDataBroker dataBroker)
            throws InterruptedException, ExecutionException, TimeoutException {

        DOMDataWriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();

        final ContainerNode topNode = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(Top.QNAME)).build();
        final YangInstanceIdentifier topPath = YangInstanceIdentifier.of(Top.QNAME);
        writeTx.put(LogicalDatastoreType.CONFIGURATION, topPath, topNode);

        final QName name = QName.create(TopLevelList.QNAME, "name");
        final YangInstanceIdentifier listPath = YangInstanceIdentifier.builder(topPath)
                .node(TopLevelList.QNAME).build();
        final MapEntryNode listEntryNode = ImmutableNodes.mapEntry(TopLevelList.QNAME, name, "one");
        final MapNode listNode = ImmutableNodes.mapNodeBuilder(TopLevelList.QNAME).addChild(listEntryNode).build();
        writeTx.merge(LogicalDatastoreType.CONFIGURATION, listPath, listNode);
        writeTx.commit().get(5, TimeUnit.SECONDS);

        verifyDataInStore(dataBroker.newReadWriteTransaction(), YangInstanceIdentifier.builder(listPath)
                .nodeWithKey(TopLevelList.QNAME, name, "one").build(), listEntryNode);

        writeTx = dataBroker.newWriteOnlyTransaction();
        writeTx.delete(LogicalDatastoreType.CONFIGURATION, topPath);
        writeTx.commit().get(5, TimeUnit.SECONDS);

        DOMDataReadWriteTransaction readTx = dataBroker.newReadWriteTransaction();
        assertFalse(readTx.exists(LogicalDatastoreType.CONFIGURATION, topPath).get(5, TimeUnit.SECONDS));
        assertTrue(readTx.cancel());
    }

    private static void writeNetconfNode(final String cacheDir, final DataBroker databroker)
            throws InterruptedException, ExecutionException, TimeoutException {
        final NetconfNode netconfNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(1234))
                .setActorResponseWaitTime(10)
                .setTcpOnly(Boolean.TRUE)
                .setSchemaless(Boolean.FALSE)
                .setKeepaliveDelay(0L)
                .setConnectionTimeoutMillis(5000L)
                .setDefaultRequestTimeoutMillis(5000L)
                .setMaxConnectionAttempts(1L)
                .setCredentials(new LoginPwUnencryptedBuilder().setLoginPasswordUnencrypted(
                        new LoginPasswordUnencryptedBuilder().setUsername("user").setPassword("pass").build()).build())
                .setSchemaCacheDirectory(cacheDir)
                .build();
        final Node node = new NodeBuilder().setNodeId(NODE_ID).addAugmentation(NetconfNode.class, netconfNode).build();

        final WriteTransaction writeTx = databroker.newWriteOnlyTransaction();
        writeTx.put(LogicalDatastoreType.CONFIGURATION, NODE_INSTANCE_ID, node);
        writeTx.commit().get(5, TimeUnit.SECONDS);
    }

    private static void verifyDataInStore(final DOMDataReadTransaction readTx, final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> expNode) throws InterruptedException, ExecutionException, TimeoutException {
        final Optional<NormalizedNode<?, ?>> read = readTx.read(LogicalDatastoreType.CONFIGURATION, path)
                .get(5, TimeUnit.SECONDS);
        assertTrue(read.isPresent());
        assertEquals(expNode, read.get());

        final Boolean exists = readTx.exists(LogicalDatastoreType.CONFIGURATION, path).get(5, TimeUnit.SECONDS);
        assertTrue(exists);
    }

    private static void verifyTopologyNodesCreated(final DataBroker dataBroker) {
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            try (ReadOnlyTransaction readTx = dataBroker.newReadOnlyTransaction()) {
                Optional<Network> configTopology = readTx.read(LogicalDatastoreType.CONFIGURATION,
                        NetconfTopologyUtils.createTopologyListPath(TOPOLOGY_ID)).get(3, TimeUnit.SECONDS);
                Optional<Network> operTopology = readTx.read(LogicalDatastoreType.OPERATIONAL,
                        NetconfTopologyUtils.createTopologyListPath(TOPOLOGY_ID)).get(3, TimeUnit.SECONDS);
                return configTopology.isPresent() && operTopology.isPresent();
            }
        });
    }

    private AbstractConcurrentDataBrokerTest newDataBrokerTest() throws Exception {
        AbstractConcurrentDataBrokerTest dataBrokerTest = new AbstractConcurrentDataBrokerTest(true) {
            @Override
            protected Iterable<YangModuleInfo> getModuleInfos() throws Exception {
                return ImmutableSet.of(BindingReflections.getModuleInfo(NetconfNode.class),
                        BindingReflections.getModuleInfo(Networks.class),
                        BindingReflections.getModuleInfo(Network.class),
                        BindingReflections.getModuleInfo(Keystore.class),
                        topModuleInfo);
            }
        };

        dataBrokerTest.setup();
        return dataBrokerTest;
    }

    private void awaitMountPointNotPresent(final DOMMountPointService mountPointService) {
        await().atMost(5, TimeUnit.SECONDS).until(
            () -> !mountPointService.getMountPoint(yangNodeInstanceId).isPresent());
    }

    private static DOMDataBroker getDOMDataBroker(final DOMMountPoint mountPoint) {
        return getMountPointService(mountPoint, DOMDataBroker.class);
    }

    private static DOMRpcService getDOMRpcService(final DOMMountPoint mountPoint) {
        return getMountPointService(mountPoint, DOMRpcService.class);
    }

    private static <T extends DOMService> T getMountPointService(final DOMMountPoint mountPoint,
            final Class<T> serviceClass) {
        final Optional<T> maybeService = mountPoint.getService(serviceClass);
        assertTrue(maybeService.isPresent());
        return maybeService.get();
    }

    private DOMMountPoint awaitMountPoint(final DOMMountPointService mountPointService) {
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            return mountPointService.getMountPoint(yangNodeInstanceId).isPresent();
        });

        return mountPointService.getMountPoint(yangNodeInstanceId).get();
    }

    private RpcDefinition findRpcDefinition(final String rpc) {
        Module topModule = deviceSchemaContext.findModule(TOP_MODULE_NAME, topModuleInfo.getName().getRevision()).get();
        RpcDefinition rpcDefinition = null;
        for (RpcDefinition def: topModule.getRpcs()) {
            if (def.getQName().getLocalName().equals(rpc)) {
                rpcDefinition = def;
                break;
            }
        }

        assertNotNull(rpc + " rpc not found in " + topModule.getRpcs(), rpcDefinition);
        return rpcDefinition;
    }

    private static class TopDOMRpcImplementation implements DOMRpcImplementation {
        private volatile SettableFuture<Entry<DOMRpcIdentifier, NormalizedNode<?, ?>>> rpcInvokedFuture;
        private volatile CheckedFuture<DOMRpcResult, DOMRpcException> returnFuture;

        @Override
        public CheckedFuture<DOMRpcResult, DOMRpcException> invokeRpc(final DOMRpcIdentifier rpc,
                final NormalizedNode<?, ?> input) {
            rpcInvokedFuture.set(new SimpleEntry<>(rpc, input));
            return returnFuture;
        }

        void init(final CheckedFuture<DOMRpcResult, DOMRpcException> retFuture) {
            this.returnFuture = retFuture;
            rpcInvokedFuture = SettableFuture.create();
        }

        void verify(final DOMRpcIdentifier expRpc, final NormalizedNode<?, ?> expInput)
                throws InterruptedException, ExecutionException, TimeoutException {
            final Entry<DOMRpcIdentifier, NormalizedNode<?, ?>> actual = rpcInvokedFuture.get(5, TimeUnit.SECONDS);
            assertEquals(expRpc, actual.getKey());
            assertEquals(expInput, actual.getValue());
        }
    }
}
