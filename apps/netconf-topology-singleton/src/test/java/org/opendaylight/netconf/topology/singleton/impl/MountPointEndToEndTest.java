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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.typesafe.config.ConfigFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.Transaction;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.TransactionChainListener;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadOperations;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.mdsal.dom.api.DOMRpcIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementation;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMService;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceRegistration;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.mdsal.singleton.dom.impl.DOMClusterSingletonServiceProviderImpl;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultSchemaResourceManager;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringRpcException;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.spi.DefaultNetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.GetTopInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.GetTopOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.PutTopInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.Top;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.two.level.list.TopLevelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.two.level.list.TopLevelListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.two.level.list.TopLevelListKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests netconf mount points end-to-end.
 *
 * @author Thomas Pantelis
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class MountPointEndToEndTest extends AbstractBaseSchemasTest {
    private static final Logger LOG = LoggerFactory.getLogger(MountPointEndToEndTest.class);

    private static final String TOP_MODULE_NAME = "opendaylight-mdsal-list-test";
    private static final String ACTOR_SYSTEM_NAME = "test";
    private static final String TOPOLOGY_ID = NetconfNodeUtils.DEFAULT_TOPOLOGY_NAME;
    private static final @NonNull KeyedInstanceIdentifier<Node, NodeKey> NODE_INSTANCE_ID =
        NetconfTopologyUtils.createTopologyNodeListPath(new NodeKey(new NodeId("node-id")), TOPOLOGY_ID);

    private static final String TEST_ROOT_DIRECTORY = "test-cache-root";
    private static final String TEST_DEFAULT_SUBDIR = "test-schema";

    @Mock private RpcProviderService mockRpcProviderService;
    @Mock private NetconfClientDispatcher mockClientDispatcher;
    @Mock private AAAEncryptionService mockEncryptionService;
    @Mock private ScheduledExecutorService mockKeepaliveExecutor;
    @Mock private DeviceActionFactory deviceActionFactory;
    @Mock private CredentialProvider credentialProvider;
    @Mock private SslHandlerFactoryProvider sslHandlerFactoryProvider;

    @Mock private DOMMountPointListener masterMountPointListener;
    private final DOMMountPointService masterMountPointService = new DOMMountPointServiceImpl();
    private Rpcs.Normalized deviceRpcService;

    private DOMClusterSingletonServiceProviderImpl masterClusterSingletonServiceProvider;
    private DataBroker masterDataBroker;
    private DOMDataBroker deviceDOMDataBroker;
    private ActorSystem masterSystem;
    private NetconfTopologyManager masterNetconfTopologyManager;

    private volatile SettableFuture<MasterSalFacade> masterSalFacadeFuture = SettableFuture.create();

    @Mock private ClusterSingletonServiceProvider mockSlaveClusterSingletonServiceProvider;
    @Mock private ClusterSingletonServiceRegistration mockSlaveClusterSingletonServiceReg;
    @Mock private DOMMountPointListener slaveMountPointListener;
    private final DOMMountPointService slaveMountPointService = new DOMMountPointServiceImpl();
    private DataBroker slaveDataBroker;
    private ActorSystem slaveSystem;
    private NetconfTopologyManager slaveNetconfTopologyManager;
    private final SettableFuture<NetconfTopologyContext> slaveNetconfTopologyContextFuture = SettableFuture.create();
    private TransactionChain slaveTxChain;

    private NetconfClientConfigurationBuilderFactory builderFactory;
    private final EventExecutor eventExecutor = GlobalEventExecutor.INSTANCE;
    private EffectiveModelContext deviceSchemaContext;
    private YangModuleInfo topModuleInfo;
    private QName putTopRpcSchemaPath;
    private QName getTopRpcSchemaPath;
    private BindingNormalizedNodeSerializer bindingToNormalized;
    private YangInstanceIdentifier yangNodeInstanceId;
    private final TopDOMRpcImplementation topRpcImplementation = new TopDOMRpcImplementation();
    private final ContainerNode getTopInput = ImmutableNodes.containerNode(GetTopInput.QNAME);

    private SchemaResourceManager resourceManager;

    @Before
    public void setUp() throws Exception {
        deleteCacheDir();

        resourceManager = new DefaultSchemaResourceManager(new DefaultYangParserFactory(), TEST_ROOT_DIRECTORY,
            TEST_DEFAULT_SUBDIR);

        topModuleInfo = BindingRuntimeHelpers.getYangModuleInfo(Top.class);

        deviceSchemaContext = BindingRuntimeHelpers.createEffectiveModel(Top.class);

        final var router = new DOMRpcRouter(FixedDOMSchemaService.of(deviceSchemaContext));

        putTopRpcSchemaPath = findRpcDefinition("put-top").getQName();
        getTopRpcSchemaPath = findRpcDefinition("get-top").getQName();

        router.getRpcProviderService().registerRpcImplementation(topRpcImplementation,
                DOMRpcIdentifier.create(putTopRpcSchemaPath), DOMRpcIdentifier.create(getTopRpcSchemaPath));

        final var rpcService = router.getRpcService();
        deviceRpcService = new Rpcs.Normalized() {
            @Override
            public ListenableFuture<? extends DOMRpcResult> invokeRpc(final QName type, final ContainerNode input) {
                return rpcService.invokeRpc(type, input);
            }

            @Override
            public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(
                    final T listener) {
                return rpcService.registerRpcListener(listener);
            }
        };

        builderFactory = new DefaultNetconfClientConfigurationBuilderFactory(mockEncryptionService, credentialProvider,
            sslHandlerFactoryProvider);

        setupMaster();

        setupSlave();

        yangNodeInstanceId = bindingToNormalized.toYangInstanceIdentifier(NODE_INSTANCE_ID);
        doReturn(mock(Future.class)).when(mockClientDispatcher).createClient(any());

        LOG.info("****** Setup complete");
    }

    private static void deleteCacheDir() {
        FileUtils.deleteQuietly(new File(TEST_ROOT_DIRECTORY));
    }

    @After
    public void tearDown() throws Exception {
        deleteCacheDir();
        TestKit.shutdownActorSystem(slaveSystem, true);
        TestKit.shutdownActorSystem(masterSystem, true);
    }

    private void setupMaster() throws Exception {
        final var dataBrokerTest = newDataBrokerTest();
        masterDataBroker = dataBrokerTest.getDataBroker();
        deviceDOMDataBroker = dataBrokerTest.getDomBroker();
        bindingToNormalized = dataBrokerTest.getDataBrokerTestCustomizer().getAdapterContext().currentSerializer();

        masterSystem = ActorSystem.create(ACTOR_SYSTEM_NAME, ConfigFactory.load().getConfig("Master"));

        masterClusterSingletonServiceProvider = new DOMClusterSingletonServiceProviderImpl();
        masterClusterSingletonServiceProvider.initializeProvider();

        final var resources =  resourceManager.getSchemaResources(TEST_DEFAULT_SUBDIR, "test");
        resources.getSchemaRegistry().registerSchemaSource(
            id -> Futures.immediateFuture(YangTextSchemaSource.delegateForCharSource(id,
                    topModuleInfo.getYangTextCharSource())),
            PotentialSchemaSource.create(new SourceIdentifier(TOP_MODULE_NAME,
                    topModuleInfo.getName().getRevision().map(Revision::toString).orElse(null)),
                YangTextSchemaSource.class, 1));

        masterNetconfTopologyManager = new NetconfTopologyManager(BASE_SCHEMAS, masterDataBroker,
                masterClusterSingletonServiceProvider, mockKeepaliveExecutor, MoreExecutors.directExecutor(),
                masterSystem, eventExecutor, mockClientDispatcher, masterMountPointService,
                mockEncryptionService, mockRpcProviderService, deviceActionFactory, resourceManager, builderFactory,
                TOPOLOGY_ID, Uint16.ZERO) {
            @Override
            protected NetconfTopologyContext newNetconfTopologyContext(final NetconfTopologySetup setup,
                    final ServiceGroupIdentifier serviceGroupIdent, final Timeout actorResponseWaitTime,
                    final DeviceActionFactory deviceActionFact) {
                final var context = super.newNetconfTopologyContext(setup, serviceGroupIdent, actorResponseWaitTime,
                    deviceActionFact);
                final var spiedContext = spy(context);
                final var spiedSingleton = spy(context.getTopologySingleton());
                doAnswer(invocation -> {
                    final var spiedFacade = (MasterSalFacade) spy(invocation.callRealMethod());
                    doReturn(deviceDOMDataBroker).when(spiedFacade)
                        .newDeviceDataBroker(any(MountPointContext.class), any(NetconfSessionPreferences.class));
                    masterSalFacadeFuture.set(spiedFacade);
                    return spiedFacade;
                }).when(spiedSingleton).createSalFacade(any(boolean.class));
                doReturn(spiedSingleton).when(spiedContext).getTopologySingleton();
                return spiedContext;
            }
        };

        verifyTopologyNodesCreated(masterDataBroker);
    }

    private void setupSlave() throws Exception {
        AbstractConcurrentDataBrokerTest dataBrokerTest = newDataBrokerTest();
        slaveDataBroker = dataBrokerTest.getDataBroker();

        slaveSystem = ActorSystem.create(ACTOR_SYSTEM_NAME, ConfigFactory.load().getConfig("Slave"));

        doReturn(mockSlaveClusterSingletonServiceReg).when(mockSlaveClusterSingletonServiceProvider)
                .registerClusterSingletonService(any());

        slaveNetconfTopologyManager = new NetconfTopologyManager(BASE_SCHEMAS, slaveDataBroker,
                mockSlaveClusterSingletonServiceProvider, mockKeepaliveExecutor, MoreExecutors.directExecutor(),
                slaveSystem, eventExecutor, mockClientDispatcher, slaveMountPointService,
                mockEncryptionService, mockRpcProviderService, deviceActionFactory, resourceManager, builderFactory,
                TOPOLOGY_ID, Uint16.ZERO) {
            @Override
            protected NetconfTopologyContext newNetconfTopologyContext(final NetconfTopologySetup setup,
                final ServiceGroupIdentifier serviceGroupIdent, final Timeout actorResponseWaitTime,
                final DeviceActionFactory actionFactory) {
                NetconfTopologyContext spiedContext = spy(super.newNetconfTopologyContext(setup, serviceGroupIdent,
                    actorResponseWaitTime, actionFactory));

                slaveNetconfTopologyContextFuture.set(spiedContext);
                return spiedContext;
            }
        };

        verifyTopologyNodesCreated(slaveDataBroker);

        slaveTxChain = slaveDataBroker.createTransactionChain(new TransactionChainListener() {
            @Override
            public void onTransactionChainSuccessful(final TransactionChain chain) {
            }

            @Override
            public void onTransactionChainFailed(final TransactionChain chain, final Transaction transaction,
                    final Throwable cause) {
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

    private MasterSalFacade testMaster() throws Exception {
        LOG.info("****** Testing master");

        writeNetconfNode(TEST_DEFAULT_SUBDIR, masterDataBroker);

        final var masterSalFacade = masterSalFacadeFuture.get(5, TimeUnit.SECONDS);
        masterSalFacade.onDeviceConnected(new NetconfDeviceSchema(NetconfDeviceCapabilities.empty(),
            MountPointContext.of(deviceSchemaContext)),
            NetconfSessionPreferences.fromStrings(List.of(CapabilityURN.CANDIDATE)),
            new RemoteDeviceServices(deviceRpcService, null));

        final var masterMountPoint = awaitMountPoint(masterMountPointService);

        LOG.info("****** Testing master DOMDataBroker operations");

        testDOMDataBrokerOperations(getDOMDataBroker(masterMountPoint));

        LOG.info("****** Testing master DOMRpcService");

        testDOMRpcService(getDOMRpcService(masterMountPoint));
        return masterSalFacade;
    }

    private void testSlave() throws Exception {
        LOG.info("****** Testing slave");

        writeNetconfNode("slave", slaveDataBroker);

        verify(mockSlaveClusterSingletonServiceProvider, timeout(5000)).registerClusterSingletonService(any());

        // Since the master and slave use separate DataBrokers we need to copy the master's oper node to the slave.
        // This is essentially what happens in a clustered environment but we'll use a DTCL here.

        masterDataBroker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL, NODE_INSTANCE_ID), changes -> {
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

        LOG.info("****** Testing slave DOMDataBroker operations");

        testDOMDataBrokerOperations(getDOMDataBroker(slaveMountPoint));

        LOG.info("****** Testing slave DOMRpcService");

        testDOMRpcService(getDOMRpcService(slaveMountPoint));
    }

    private MasterSalFacade testMasterNodeUpdated() throws Exception {
        LOG.info("****** Testing update master node");

        masterMountPointService.registerProvisionListener(masterMountPointListener);
        slaveMountPointService.registerProvisionListener(slaveMountPointListener);

        masterSalFacadeFuture = SettableFuture.create();
        writeNetconfNode(TEST_DEFAULT_SUBDIR, masterDataBroker);

        verify(masterMountPointListener, timeout(5000)).onMountPointRemoved(yangNodeInstanceId);

        final var masterSalFacade = masterSalFacadeFuture.get(5, TimeUnit.SECONDS);
        masterSalFacade.onDeviceConnected(
            new NetconfDeviceSchema(NetconfDeviceCapabilities.empty(), MountPointContext.of(deviceSchemaContext)),
            NetconfSessionPreferences.fromStrings(List.of(CapabilityURN.CANDIDATE)),
            new RemoteDeviceServices(deviceRpcService, null));

        verify(masterMountPointListener, timeout(5000)).onMountPointCreated(yangNodeInstanceId);

        verify(slaveMountPointListener, timeout(5000)).onMountPointRemoved(yangNodeInstanceId);
        verify(slaveMountPointListener, timeout(5000)).onMountPointCreated(yangNodeInstanceId);

        return masterSalFacade;
    }

    private void testMasterDisconnected(final MasterSalFacade masterSalFacade) throws Exception {
        LOG.info("****** Testing master disconnected");

        masterSalFacade.onDeviceDisconnected();

        awaitMountPointNotPresent(masterMountPointService);

        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            try (ReadTransaction readTx = masterDataBroker.newReadOnlyTransaction()) {
                Optional<Node> node = readTx.read(LogicalDatastoreType.OPERATIONAL,
                        NODE_INSTANCE_ID).get(5, TimeUnit.SECONDS);
                assertTrue(node.isPresent());
                final NetconfNode netconfNode = node.orElseThrow().augmentation(NetconfNode.class);
                return netconfNode.getConnectionStatus() != ConnectionStatus.Connected;
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
        testPutTopRpc(domRpcService, new DefaultDOMRpcResult((ContainerNode)null));
        testPutTopRpc(domRpcService, null);
        testPutTopRpc(domRpcService, new DefaultDOMRpcResult(ImmutableList.of(
                RpcResultBuilder.newError(ErrorType.APPLICATION, new ErrorTag("tag1"), "error1"),
                RpcResultBuilder.newError(ErrorType.APPLICATION, new ErrorTag("tag2"), "error2"))));

        testGetTopRpc(domRpcService, new DefaultDOMRpcResult(bindingToNormalized.toNormalizedNodeRpcData(
                new GetTopOutputBuilder().setTopLevelList(oneTopLevelList()).build())));

        testFailedRpc(domRpcService, getTopRpcSchemaPath, getTopInput);
    }

    private void testPutTopRpc(final DOMRpcService domRpcService, final DOMRpcResult result)
            throws InterruptedException, ExecutionException, TimeoutException {
        ContainerNode putTopInput = bindingToNormalized.toNormalizedNodeRpcData(
                new PutTopInputBuilder().setTopLevelList(oneTopLevelList()).build());
        testRpc(domRpcService, putTopRpcSchemaPath, putTopInput, result);
    }

    private static Map<TopLevelListKey, TopLevelList> oneTopLevelList() {
        final TopLevelListKey key = new TopLevelListKey("one");
        return ImmutableMap.of(key, new TopLevelListBuilder().withKey(key).build());
    }

    private void testGetTopRpc(final DOMRpcService domRpcService, final DOMRpcResult result)
            throws InterruptedException, ExecutionException, TimeoutException {
        testRpc(domRpcService, getTopRpcSchemaPath, getTopInput, result);
    }

    private void testRpc(final DOMRpcService domRpcService, final QName qname, final ContainerNode input,
            final DOMRpcResult result) throws InterruptedException, ExecutionException, TimeoutException {
        final FluentFuture<DOMRpcResult> future = result == null ? FluentFutures.immediateNullFluentFuture()
                : FluentFutures.immediateFluentFuture(result);
        final DOMRpcResult actual = invokeRpc(domRpcService, qname, input, future);
        if (result == null) {
            assertNull(actual);
            return;
        }

        assertNotNull(actual);
        assertEquals(result.value(), actual.value());

        assertEquals(result.errors().size(), actual.errors().size());
        Iterator<? extends RpcError> iter1 = result.errors().iterator();
        Iterator<? extends RpcError> iter2 = actual.errors().iterator();
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

    private void testFailedRpc(final DOMRpcService domRpcService, final QName qname, final ContainerNode input)
            throws InterruptedException, TimeoutException {
        try {
            invokeRpc(domRpcService, qname, input, Futures.immediateFailedFuture(new ClusteringRpcException("mock")));
            fail("Expected exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ClusteringRpcException);
            assertEquals("mock", e.getCause().getMessage());
        }
    }

    private DOMRpcResult invokeRpc(final DOMRpcService domRpcService, final QName qname, final ContainerNode input,
            final ListenableFuture<DOMRpcResult> returnFuture)
                throws InterruptedException, ExecutionException, TimeoutException {
        topRpcImplementation.init(returnFuture);
        final ListenableFuture<? extends DOMRpcResult> resultFuture = domRpcService.invokeRpc(qname, input);

        topRpcImplementation.verify(DOMRpcIdentifier.create(qname), input);

        return resultFuture.get(5, TimeUnit.SECONDS);
    }

    private static void testDOMDataBrokerOperations(final DOMDataBroker dataBroker)
            throws InterruptedException, ExecutionException, TimeoutException {

        DOMDataTreeWriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();

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

        DOMDataTreeReadWriteTransaction readTx = dataBroker.newReadWriteTransaction();
        assertFalse(readTx.exists(LogicalDatastoreType.CONFIGURATION, topPath).get(5, TimeUnit.SECONDS));
        assertTrue(readTx.cancel());
    }

    private static void writeNetconfNode(final String cacheDir, final DataBroker dataBroker) throws Exception {
        putData(dataBroker, NODE_INSTANCE_ID, new NodeBuilder()
            .withKey(NODE_INSTANCE_ID.getKey())
            .addAugmentation(new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(1234)))
                .setActorResponseWaitTime(Uint16.valueOf(10))
                .setTcpOnly(Boolean.TRUE)
                .setSchemaless(Boolean.FALSE)
                .setKeepaliveDelay(Uint32.ZERO)
                .setConnectionTimeoutMillis(Uint32.valueOf(5000))
                .setDefaultRequestTimeoutMillis(Uint32.valueOf(5000))
                .setMaxConnectionAttempts(Uint32.ONE)
                .setCredentials(new LoginPwUnencryptedBuilder()
                    .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                        .setUsername("user")
                        .setPassword("pass")
                        .build())
                    .build())
                .setSchemaCacheDirectory(cacheDir)
                .build())
            .build());
    }

    private static <T extends DataObject> void putData(final DataBroker databroker, final InstanceIdentifier<T> path,
            final T data) throws Exception {
        final var writeTx = databroker.newWriteOnlyTransaction();
        writeTx.put(LogicalDatastoreType.CONFIGURATION, path, data);
        writeTx.commit().get(5, TimeUnit.SECONDS);
    }

    private static void verifyDataInStore(final DOMDataTreeReadOperations readTx, final YangInstanceIdentifier path,
            final NormalizedNode expNode) throws InterruptedException, ExecutionException, TimeoutException {
        assertEquals(Optional.of(expNode), readTx.read(LogicalDatastoreType.CONFIGURATION, path)
            .get(5, TimeUnit.SECONDS));
        assertTrue(readTx.exists(LogicalDatastoreType.CONFIGURATION, path).get(5, TimeUnit.SECONDS));
    }

    private static void verifyTopologyNodesCreated(final DataBroker dataBroker) {
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            try (ReadTransaction readTx = dataBroker.newReadOnlyTransaction()) {
                return readTx.exists(LogicalDatastoreType.OPERATIONAL,
                    NetconfTopologyUtils.createTopologyListPath(TOPOLOGY_ID)).get(3, TimeUnit.SECONDS);
            }
        });
    }

    private AbstractConcurrentDataBrokerTest newDataBrokerTest() throws Exception {
        final var dataBrokerTest = new AbstractConcurrentDataBrokerTest(true) {
            @Override
            protected Set<YangModuleInfo> getModuleInfos() {
                return Set.of(
                    BindingRuntimeHelpers.getYangModuleInfo(NetconfNode.class),
                    BindingRuntimeHelpers.getYangModuleInfo(NetworkTopology.class),
                    BindingRuntimeHelpers.getYangModuleInfo(Keystore.class),
                    topModuleInfo);
            }
        };

        dataBrokerTest.setup();

        final var path = NetconfTopologyUtils.createTopologyListPath(TOPOLOGY_ID);
        putData(dataBrokerTest.getDataBroker(), path, new TopologyBuilder().withKey(path.getKey()).build());
        return dataBrokerTest;
    }

    private void awaitMountPointNotPresent(final DOMMountPointService mountPointService) {
        await().atMost(5, TimeUnit.SECONDS).until(
            () -> mountPointService.getMountPoint(yangNodeInstanceId).isEmpty());
    }

    private static DOMDataBroker getDOMDataBroker(final DOMMountPoint mountPoint) {
        return getMountPointService(mountPoint, DOMDataBroker.class);
    }

    private static DOMRpcService getDOMRpcService(final DOMMountPoint mountPoint) {
        return getMountPointService(mountPoint, DOMRpcService.class);
    }

    private static DOMActionService getDomActionService(final DOMMountPoint mountPoint) {
        return getMountPointService(mountPoint, DOMActionService.class);
    }

    private static <T extends DOMService> T getMountPointService(final DOMMountPoint mountPoint,
            final Class<T> serviceClass) {
        return mountPoint.getService(serviceClass).orElseThrow();
    }

    private DOMMountPoint awaitMountPoint(final DOMMountPointService mountPointService) {
        await().atMost(5, TimeUnit.SECONDS).until(() ->
                mountPointService.getMountPoint(yangNodeInstanceId).isPresent());

        return mountPointService.getMountPoint(yangNodeInstanceId).orElseThrow();
    }

    private RpcDefinition findRpcDefinition(final String rpc) {
        Module topModule = deviceSchemaContext.findModule(TOP_MODULE_NAME, topModuleInfo.getName().getRevision())
            .orElseThrow();
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
        private volatile SettableFuture<Entry<DOMRpcIdentifier, NormalizedNode>> rpcInvokedFuture;
        private volatile ListenableFuture<DOMRpcResult> returnFuture;

        @Override
        public ListenableFuture<DOMRpcResult> invokeRpc(final DOMRpcIdentifier rpc, final ContainerNode input) {
            rpcInvokedFuture.set(Map.entry(rpc, input));
            return returnFuture;
        }

        void init(final ListenableFuture<DOMRpcResult> retFuture) {
            returnFuture = retFuture;
            rpcInvokedFuture = SettableFuture.create();
        }

        void verify(final DOMRpcIdentifier expRpc, final NormalizedNode expInput)
                throws InterruptedException, ExecutionException, TimeoutException {
            final Entry<DOMRpcIdentifier, NormalizedNode> actual = rpcInvokedFuture.get(5, TimeUnit.SECONDS);
            assertEquals(expRpc, actual.getKey());
            assertEquals(expInput, actual.getValue());
        }
    }
}
