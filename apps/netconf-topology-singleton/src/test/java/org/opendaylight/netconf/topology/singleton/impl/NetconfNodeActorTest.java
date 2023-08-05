/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status.Failure;
import akka.actor.Status.Success;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.controller.cluster.schema.provider.impl.YangTextSchemaSourceSerializationProxy;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionException;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.mdsal.dom.spi.SimpleDOMActionResult;
import org.opendaylight.netconf.client.mdsal.NetconfDevice.SchemaResourcesDTO;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Actions;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringActionException;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringRpcException;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup.NetconfTopologySetupBuilder;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.netconf.topology.singleton.messages.NotMasterException;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.RegisterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.UnregisterSlaveMountPoint;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.TextToIRTransformer;
import scala.concurrent.Await;
import scala.concurrent.Future;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfNodeActorTest extends AbstractBaseSchemasTest {

    private static final Timeout TIMEOUT = Timeout.create(Duration.ofSeconds(5));
    private static final SourceIdentifier SOURCE_IDENTIFIER1 = new SourceIdentifier("yang1");
    private static final SourceIdentifier SOURCE_IDENTIFIER2 = new SourceIdentifier("yang2");

    private ActorSystem system = ActorSystem.create();
    private final TestKit testKit = new TestKit(system);

    private ActorRef masterRef;
    private RemoteDeviceId remoteDeviceId;
    private final SharedSchemaRepository masterSchemaRepository = new SharedSchemaRepository("master");

    @Mock
    private Rpcs.Normalized mockDOMRpcService;
    @Mock
    private Actions.Normalized mockDOMActionService;
    @Mock
    private DOMMountPointService mockMountPointService;
    @Mock
    private DOMMountPointService.DOMMountPointBuilder mockMountPointBuilder;
    @Mock
    private ObjectRegistration<DOMMountPoint> mockMountPointReg;
    @Mock
    private DOMDataBroker mockDOMDataBroker;
    @Mock
    private NetconfDataTreeService netconfService;
    @Mock
    private SchemaSourceRegistration<?> mockSchemaSourceReg1;
    @Mock
    private SchemaSourceRegistration<?> mockSchemaSourceReg2;
    @Mock
    private SchemaSourceRegistry mockRegistry;
    @Mock
    private EffectiveModelContextFactory mockSchemaContextFactory;
    @Mock
    private SchemaRepository mockSchemaRepository;
    @Mock
    private EffectiveModelContext mockSchemaContext;
    @Mock
    private SchemaResourcesDTO schemaResourceDTO;

    @Before
    public void setup() {
        remoteDeviceId = new RemoteDeviceId("netconf-topology",
                new InetSocketAddress(InetAddresses.forString("127.0.0.1"), 9999));

        masterSchemaRepository.registerSchemaSourceListener(
                TextToIRTransformer.create(masterSchemaRepository, masterSchemaRepository));

        doReturn(masterSchemaRepository).when(schemaResourceDTO).getSchemaRepository();
        doReturn(mockRegistry).when(schemaResourceDTO).getSchemaRegistry();
        final NetconfTopologySetup setup = NetconfTopologySetupBuilder.create()
            .setActorSystem(system)
            .setIdleTimeout(Duration.ofSeconds(1))
            .setSchemaResourceDTO(schemaResourceDTO)
            .setBaseSchemas(BASE_SCHEMAS)
            .build();

        final Props props = NetconfNodeActor.props(setup, remoteDeviceId, TIMEOUT, mockMountPointService);

        masterRef = TestActorRef.create(system, props, "master_messages");

        resetMountPointMocks();

        doReturn(mockMountPointBuilder).when(mockMountPointService).createMountPoint(any());

        doReturn(mockSchemaSourceReg1).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        doReturn(mockSchemaSourceReg2).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER2));

        doReturn(mockSchemaContextFactory).when(mockSchemaRepository)
                .createEffectiveModelContextFactory();
    }

    @After
    public void teardown() {
        TestKit.shutdownActorSystem(system, true);
        system = null;
    }

    @Test
    public void testInitializeAndRefreshMasterData() {

        // Test CreateInitialMasterActorData.

        initializeMaster(new ArrayList<>());

        // Test RefreshSetupMasterActorData.

        final RemoteDeviceId newRemoteDeviceId = new RemoteDeviceId("netconf-topology2",
                new InetSocketAddress(InetAddresses.forString("127.0.0.2"), 9999));

        final NetconfTopologySetup newSetup = NetconfTopologySetupBuilder.create()
            .setBaseSchemas(BASE_SCHEMAS)
            .setSchemaResourceDTO(schemaResourceDTO)
            .setActorSystem(system)
            .build();

        masterRef.tell(new RefreshSetupMasterActorData(newSetup, newRemoteDeviceId), testKit.getRef());

        testKit.expectMsgClass(MasterActorDataInitialized.class);
    }

    @Test
    public void testAskForMasterMountPoint() {

        // Test with master not setup yet.

        final TestKit kit = new TestKit(system);

        masterRef.tell(new AskForMasterMountPoint(kit.getRef()), kit.getRef());

        final Failure failure = kit.expectMsgClass(Failure.class);
        assertTrue(failure.cause() instanceof NotMasterException);

        // Now initialize - master should send the RegisterMountPoint message.

        List<SourceIdentifier> sourceIdentifiers = List.of(new SourceIdentifier("testID"));
        initializeMaster(sourceIdentifiers);

        masterRef.tell(new AskForMasterMountPoint(kit.getRef()), kit.getRef());

        final RegisterMountPoint registerMountPoint = kit.expectMsgClass(RegisterMountPoint.class);

        assertEquals(sourceIdentifiers, registerMountPoint.getSourceIndentifiers());
    }

    @Test
    public void testRegisterAndUnregisterMountPoint() throws Exception {

        ActorRef slaveRef = registerSlaveMountPoint();

        // Unregister

        slaveRef.tell(new UnregisterSlaveMountPoint(), testKit.getRef());

        verify(mockMountPointReg, timeout(5000)).close();
        verify(mockSchemaSourceReg1, timeout(1000)).close();
        verify(mockSchemaSourceReg2, timeout(1000)).close();

        // Test registration with another interleaved registration that completes while the first registration
        // is resolving the schema context.

        reset(mockSchemaSourceReg1, mockRegistry, mockSchemaRepository);
        resetMountPointMocks();

        doReturn(mockSchemaSourceReg1).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));

        final SchemaSourceRegistration<?> newMockSchemaSourceReg = mock(SchemaSourceRegistration.class);

        final EffectiveModelContextFactory newMockSchemaContextFactory = mock(EffectiveModelContextFactory.class);
        doReturn(Futures.immediateFuture(mockSchemaContext))
                .when(newMockSchemaContextFactory).createEffectiveModelContext(anyCollection());

        doAnswer(unused -> {
            SettableFuture<SchemaContext> future = SettableFuture.create();
            new Thread(() -> {
                doReturn(newMockSchemaSourceReg).when(mockRegistry).registerSchemaSource(any(),
                        withSourceId(SOURCE_IDENTIFIER1));

                doReturn(newMockSchemaContextFactory).when(mockSchemaRepository)
                        .createEffectiveModelContextFactory();

                slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1), masterRef),
                        testKit.getRef());

                future.set(mockSchemaContext);
            }).start();
            return future;
        }).when(mockSchemaContextFactory).createEffectiveModelContext(anyCollection());

        doReturn(mockSchemaContextFactory).when(mockSchemaRepository)
                .createEffectiveModelContextFactory();

        slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1), masterRef), testKit.getRef());

        verify(mockMountPointBuilder, timeout(5000)).register();
        verify(mockMountPointBuilder, after(500)).addService(eq(DOMDataBroker.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMRpcService.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMActionService.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMNotificationService.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMSchemaService.class), any());
        verify(mockSchemaSourceReg1).close();
        verify(mockRegistry, times(2)).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        verify(mockSchemaRepository, times(2)).createEffectiveModelContextFactory();
        verifyNoMoreInteractions(mockMountPointBuilder, newMockSchemaSourceReg);

        // Stop the slave actor and verify schema source registrations are closed.

        final Future<Boolean> stopFuture = Patterns.gracefulStop(slaveRef, TIMEOUT.duration());
        Await.result(stopFuture, TIMEOUT.duration());

        verify(mockMountPointReg).close();
        verify(newMockSchemaSourceReg).close();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRegisterMountPointWithSchemaFailures() throws Exception {
        SchemaResourcesDTO schemaResourceDTO2 = mock(SchemaResourcesDTO.class);
        doReturn(mockRegistry).when(schemaResourceDTO2).getSchemaRegistry();
        doReturn(mockSchemaRepository).when(schemaResourceDTO2).getSchemaRepository();
        final NetconfTopologySetup setup = NetconfTopologySetupBuilder.create()
                .setSchemaResourceDTO(schemaResourceDTO2)
                .setBaseSchemas(BASE_SCHEMAS)
                .setActorSystem(system)
                .build();

        final ActorRef slaveRef = system.actorOf(NetconfNodeActor.props(setup, remoteDeviceId, TIMEOUT,
                mockMountPointService));

        // Test unrecoverable failure.

        doReturn(Futures.immediateFailedFuture(new SchemaResolutionException("mock")))
                .when(mockSchemaContextFactory).createEffectiveModelContext(anyCollection());

        slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1, SOURCE_IDENTIFIER2),
                masterRef), testKit.getRef());

        testKit.expectMsgClass(Success.class);

        verify(mockRegistry, timeout(5000)).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        verify(mockRegistry, timeout(5000)).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER2));

        verify(mockMountPointBuilder, after(1000).never()).register();
        verify(mockSchemaSourceReg1, timeout(1000)).close();
        verify(mockSchemaSourceReg2, timeout(1000)).close();

        // Test recoverable AskTimeoutException - schema context resolution should be retried.

        reset(mockSchemaSourceReg1, mockSchemaSourceReg2);

        doReturn(Futures.immediateFailedFuture(new SchemaResolutionException("mock",
                new AskTimeoutException("timeout"))))
            .doReturn(Futures.immediateFuture(mockSchemaContext))
            .when(mockSchemaContextFactory).createEffectiveModelContext(anyCollection());

        slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1, SOURCE_IDENTIFIER2),
                masterRef), testKit.getRef());

        testKit.expectMsgClass(Success.class);

        verify(mockMountPointBuilder, timeout(5000)).register();
        verifyNoMoreInteractions(mockSchemaSourceReg1, mockSchemaSourceReg2);

        // Test AskTimeoutException with an interleaved successful registration. The first schema context resolution
        // attempt should not be retried.

        reset(mockSchemaSourceReg1, mockSchemaSourceReg2, mockSchemaRepository, mockSchemaContextFactory);
        resetMountPointMocks();

        final EffectiveModelContextFactory mockSchemaContextFactorySuccess = mock(EffectiveModelContextFactory.class);
        doReturn(Futures.immediateFuture(mockSchemaContext))
                .when(mockSchemaContextFactorySuccess).createEffectiveModelContext(anyCollection());

        doAnswer(unused -> {
            SettableFuture<SchemaContext> future = SettableFuture.create();
            new Thread(() -> {
                doReturn(mockSchemaContextFactorySuccess).when(mockSchemaRepository)
                    .createEffectiveModelContextFactory();

                slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1, SOURCE_IDENTIFIER2),
                        masterRef), testKit.getRef());

                future.setException(new SchemaResolutionException("mock", new AskTimeoutException("timeout")));
            }).start();
            return future;
        }).when(mockSchemaContextFactory).createEffectiveModelContext(anyCollection());

        doReturn(mockSchemaContextFactory).when(mockSchemaRepository).createEffectiveModelContextFactory();

        slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1, SOURCE_IDENTIFIER2),
                masterRef), testKit.getRef());

        verify(mockMountPointBuilder, timeout(5000)).register();
        verify(mockSchemaRepository, times(2)).createEffectiveModelContextFactory();
    }

    @Test(expected = MissingSchemaSourceException.class)
    public void testMissingSchemaSourceOnMissingProvider() throws Exception {
        final SharedSchemaRepository repository = new SharedSchemaRepository("test");

        SchemaResourcesDTO schemaResourceDTO2 = mock(SchemaResourcesDTO.class);
        doReturn(repository).when(schemaResourceDTO2).getSchemaRegistry();
        doReturn(repository).when(schemaResourceDTO2).getSchemaRepository();
        final NetconfTopologySetup setup = NetconfTopologySetupBuilder.create()
            .setActorSystem(system)
            .setSchemaResourceDTO(schemaResourceDTO2)
            .setIdleTimeout(Duration.ofSeconds(1))
            .setBaseSchemas(BASE_SCHEMAS)
            .build();
        final Props props = NetconfNodeActor.props(setup, remoteDeviceId, TIMEOUT, mockMountPointService);
        ActorRef actor = TestActorRef.create(system, props, "master_messages_2");

        final SourceIdentifier sourceIdentifier = new SourceIdentifier("testID");

        final ProxyYangTextSourceProvider proxyYangProvider =
                new ProxyYangTextSourceProvider(actor, system.dispatcher(), TIMEOUT);

        final Future<YangTextSchemaSourceSerializationProxy> resolvedSchemaFuture =
                proxyYangProvider.getYangTextSchemaSource(sourceIdentifier);
        Await.result(resolvedSchemaFuture, TIMEOUT.duration());
    }

    @Test
    public void testYangTextSchemaSourceRequest() throws Exception {
        final SourceIdentifier sourceIdentifier = new SourceIdentifier("testID");

        final ProxyYangTextSourceProvider proxyYangProvider =
                new ProxyYangTextSourceProvider(masterRef, system.dispatcher(), TIMEOUT);

        final YangTextSchemaSource yangTextSchemaSource = YangTextSchemaSource.delegateForCharSource(sourceIdentifier,
                CharSource.wrap("YANG"));

        // Test success.

        final SchemaSourceRegistration<YangTextSchemaSource> schemaSourceReg = masterSchemaRepository
                .registerSchemaSource(id -> Futures.immediateFuture(yangTextSchemaSource),
                     PotentialSchemaSource.create(sourceIdentifier, YangTextSchemaSource.class, 1));

        final Future<YangTextSchemaSourceSerializationProxy> resolvedSchemaFuture =
                proxyYangProvider.getYangTextSchemaSource(sourceIdentifier);

        final YangTextSchemaSourceSerializationProxy success = Await.result(resolvedSchemaFuture, TIMEOUT.duration());

        assertEquals(sourceIdentifier, success.getRepresentation().getIdentifier());
        assertEquals("YANG", success.getRepresentation().read());

        // Test missing source failure.

        schemaSourceReg.close();

        final MissingSchemaSourceException ex = assertThrows(MissingSchemaSourceException.class,
            () -> {
                final Future<YangTextSchemaSourceSerializationProxy> failedSchemaFuture =
                        proxyYangProvider.getYangTextSchemaSource(sourceIdentifier);
                Await.result(failedSchemaFuture, TIMEOUT.duration());
            });
        assertThat(ex.getMessage(), startsWith("No providers registered for source"));
        assertThat(ex.getMessage(), containsString(sourceIdentifier.toString()));
    }

    @Test
    public void testSlaveInvokeRpc() throws Exception {

        initializeMaster(List.of(new SourceIdentifier("testID")));
        registerSlaveMountPoint();

        ArgumentCaptor<DOMRpcService> domRPCServiceCaptor = ArgumentCaptor.forClass(DOMRpcService.class);
        verify(mockMountPointBuilder).addService(eq(DOMRpcService.class), domRPCServiceCaptor.capture());

        final DOMRpcService slaveDomRPCService = domRPCServiceCaptor.getValue();
        assertTrue(slaveDomRPCService instanceof ProxyDOMRpcService);

        final QName testQName = QName.create("", "TestQname");
        final ContainerNode outputNode = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(testQName))
                .withChild(ImmutableNodes.leafNode(testQName, "foo")).build();
        final RpcError rpcError = RpcResultBuilder.newError(ErrorType.RPC, null, "Rpc invocation failed.");

        // RPC with no response output.

        doReturn(FluentFutures.immediateNullFluentFuture()).when(mockDOMRpcService).invokeRpc(any(), any());

        DOMRpcResult result = slaveDomRPCService.invokeRpc(testQName, outputNode).get(2, TimeUnit.SECONDS);

        assertEquals(null, result);

        // RPC with response output.

        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult(outputNode)))
                .when(mockDOMRpcService).invokeRpc(any(), any());

        result = slaveDomRPCService.invokeRpc(testQName, outputNode).get(2, TimeUnit.SECONDS);

        assertEquals(outputNode, result.value());
        assertTrue(result.errors().isEmpty());

        // RPC with response error.

        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult(rpcError)))
                .when(mockDOMRpcService).invokeRpc(any(), any());

        result = slaveDomRPCService.invokeRpc(testQName, outputNode).get(2, TimeUnit.SECONDS);

        assertNull(result.value());
        assertEquals(rpcError, result.errors().iterator().next());

        // RPC with response output and error.

        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult(outputNode, rpcError)))
                .when(mockDOMRpcService).invokeRpc(any(), any());

        final DOMRpcResult resultOutputError =
                slaveDomRPCService.invokeRpc(testQName, outputNode).get(2, TimeUnit.SECONDS);

        assertEquals(outputNode, resultOutputError.value());
        assertEquals(rpcError, resultOutputError.errors().iterator().next());

        // RPC failure.
        doReturn(FluentFutures.immediateFailedFluentFuture(new ClusteringRpcException("mock")))
            .when(mockDOMRpcService).invokeRpc(any(), any());
        final ListenableFuture<? extends DOMRpcResult> future = slaveDomRPCService.invokeRpc(testQName, outputNode);

        final ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        final Throwable cause = e.getCause();
        assertThat(cause, instanceOf(DOMRpcException.class));
        assertEquals("mock", cause.getMessage());
    }

    @Test
    public void testSlaveInvokeAction() throws Exception {
        initializeMaster(List.of(new SourceIdentifier("testActionID")));
        registerSlaveMountPoint();

        ArgumentCaptor<DOMActionService> domActionServiceCaptor = ArgumentCaptor.forClass(DOMActionService.class);
        verify(mockMountPointBuilder).addService(eq(DOMActionService.class), domActionServiceCaptor.capture());

        final DOMActionService slaveDomActionService = domActionServiceCaptor.getValue();
        assertTrue(slaveDomActionService instanceof ProxyDOMActionService);

        final QName testQName = QName.create("test", "2019-08-16", "TestActionQname");
        final Absolute schemaPath = Absolute.of(testQName);

        final YangInstanceIdentifier yangIIdPath = YangInstanceIdentifier.of(testQName);

        final DOMDataTreeIdentifier domDataTreeIdentifier = new DOMDataTreeIdentifier(LogicalDatastoreType.OPERATIONAL,
            yangIIdPath);

        final ContainerNode outputNode = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(testQName))
            .withChild(ImmutableNodes.leafNode(testQName, "foo")).build();

        // Action with no response output.
        doReturn(FluentFutures.immediateNullFluentFuture()).when(mockDOMActionService)
            .invokeAction(any(), any(), any());
        DOMActionResult result = slaveDomActionService.invokeAction(schemaPath, domDataTreeIdentifier, outputNode)
            .get(2, TimeUnit.SECONDS);
        assertEquals(null, result);

        // Action with response output.
        doReturn(FluentFutures.immediateFluentFuture(new SimpleDOMActionResult(outputNode))).when(mockDOMActionService)
            .invokeAction(any(), any(), any());
        result = slaveDomActionService.invokeAction(schemaPath, domDataTreeIdentifier, outputNode)
            .get(2, TimeUnit.SECONDS);

        assertEquals(Optional.of(outputNode), result.getOutput());
        assertTrue(result.getErrors().isEmpty());

        // Action failure.
        doReturn(FluentFutures.immediateFailedFluentFuture(new ClusteringActionException("mock")))
            .when(mockDOMActionService).invokeAction(any(), any(), any());
        final ListenableFuture<? extends DOMActionResult> future = slaveDomActionService.invokeAction(schemaPath,
            domDataTreeIdentifier, outputNode);

        final ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        final Throwable cause = e.getCause();
        assertThat(cause, instanceOf(DOMActionException.class));
        assertEquals("mock", cause.getMessage());
    }

    @Test
    public void testSlaveNewTransactionRequests() {
        doReturn(mock(DOMDataTreeReadTransaction.class)).when(mockDOMDataBroker).newReadOnlyTransaction();
        doReturn(mock(DOMDataTreeReadWriteTransaction.class)).when(mockDOMDataBroker).newReadWriteTransaction();
        doReturn(mock(DOMDataTreeWriteTransaction.class)).when(mockDOMDataBroker).newWriteOnlyTransaction();

        initializeMaster(List.of());
        registerSlaveMountPoint();

        ArgumentCaptor<DOMDataBroker> domDataBrokerCaptor = ArgumentCaptor.forClass(DOMDataBroker.class);
        verify(mockMountPointBuilder).addService(eq(DOMDataBroker.class), domDataBrokerCaptor.capture());

        final DOMDataBroker slaveDOMDataBroker = domDataBrokerCaptor.getValue();
        assertTrue(slaveDOMDataBroker instanceof ProxyDOMDataBroker);

        slaveDOMDataBroker.newReadOnlyTransaction();
        verify(mockDOMDataBroker).newReadOnlyTransaction();

        slaveDOMDataBroker.newReadWriteTransaction();
        verify(mockDOMDataBroker).newReadWriteTransaction();

        slaveDOMDataBroker.newWriteOnlyTransaction();
        verify(mockDOMDataBroker).newWriteOnlyTransaction();
    }

    @Test
    public void testSlaveNewNetconfDataTreeServiceRequest() {
        initializeMaster(List.of());
        registerSlaveMountPoint();

        ArgumentCaptor<NetconfDataTreeService> netconfCaptor = ArgumentCaptor.forClass(NetconfDataTreeService.class);
        verify(mockMountPointBuilder).addService(eq(NetconfDataTreeService.class), netconfCaptor.capture());

        final NetconfDataTreeService slaveNetconfService = netconfCaptor.getValue();
        assertTrue(slaveNetconfService instanceof ProxyNetconfDataTreeService);

        final YangInstanceIdentifier PATH = YangInstanceIdentifier.of();
        final LogicalDatastoreType STORE = LogicalDatastoreType.CONFIGURATION;
        final ContainerNode NODE = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create("", "cont")))
            .build();

        final FluentFuture<Optional<Object>> result = immediateFluentFuture(Optional.of(NODE));
        doReturn(result).when(netconfService).get(PATH);
        doReturn(result).when(netconfService).getConfig(PATH);
        doReturn(emptyFluentFuture()).when(netconfService).commit();

        slaveNetconfService.get(PATH);
        slaveNetconfService.getConfig(PATH);
        slaveNetconfService.lock();
        slaveNetconfService.merge(STORE, PATH, NODE, Optional.empty());
        slaveNetconfService.replace(STORE, PATH, NODE, Optional.empty());
        slaveNetconfService.create(STORE, PATH, NODE, Optional.empty());
        slaveNetconfService.delete(STORE, PATH);
        slaveNetconfService.remove(STORE, PATH);
        slaveNetconfService.discardChanges();
        slaveNetconfService.commit();

        verify(netconfService, timeout(1000)).get(PATH);
        verify(netconfService, timeout(1000)).getConfig(PATH);
        verify(netconfService, timeout(1000)).lock();
        verify(netconfService, timeout(1000)).merge(STORE, PATH, NODE, Optional.empty());
        verify(netconfService, timeout(1000)).replace(STORE, PATH, NODE, Optional.empty());
        verify(netconfService, timeout(1000)).create(STORE, PATH, NODE, Optional.empty());
        verify(netconfService, timeout(1000)).delete(STORE, PATH);
        verify(netconfService, timeout(1000)).remove(STORE, PATH);
        verify(netconfService, timeout(1000)).discardChanges();
        verify(netconfService, timeout(1000)).commit();
    }

    private ActorRef registerSlaveMountPoint() {
        SchemaResourcesDTO schemaResourceDTO2 = mock(SchemaResourcesDTO.class);
        doReturn(mockRegistry).when(schemaResourceDTO2).getSchemaRegistry();
        doReturn(mockSchemaRepository).when(schemaResourceDTO2).getSchemaRepository();
        final ActorRef slaveRef = system.actorOf(NetconfNodeActor.props(NetconfTopologySetupBuilder.create()
                .setSchemaResourceDTO(schemaResourceDTO2)
                .setActorSystem(system)
                .setBaseSchemas(BASE_SCHEMAS)
                .build(), remoteDeviceId, TIMEOUT, mockMountPointService));

        doReturn(Futures.immediateFuture(mockSchemaContext))
                .when(mockSchemaContextFactory).createEffectiveModelContext(anyCollection());

        slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1, SOURCE_IDENTIFIER2),
                masterRef), testKit.getRef());

        verify(mockMountPointBuilder, timeout(5000)).register();
        verify(mockMountPointBuilder).addService(eq(DOMSchemaService.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMDataBroker.class), any());
        verify(mockMountPointBuilder).addService(eq(NetconfDataTreeService.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMRpcService.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMNotificationService.class), any());

        testKit.expectMsgClass(Success.class);
        return slaveRef;
    }

    private void initializeMaster(final List<SourceIdentifier> sourceIdentifiers) {
        masterRef.tell(new CreateInitialMasterActorData(mockDOMDataBroker, netconfService, sourceIdentifiers,
                new RemoteDeviceServices(mockDOMRpcService, mockDOMActionService)), testKit.getRef());
        testKit.expectMsgClass(MasterActorDataInitialized.class);
    }

    private void resetMountPointMocks() {
        reset(mockMountPointReg, mockMountPointBuilder);

        doNothing().when(mockMountPointReg).close();

        doReturn(mockMountPointBuilder).when(mockMountPointBuilder).addService(any(), any());
        doReturn(mockMountPointReg).when(mockMountPointBuilder).register();
    }

    private static PotentialSchemaSource<?> withSourceId(final SourceIdentifier identifier) {
        return argThat(argument -> identifier.equals(argument.getSourceIdentifier()));
    }
}
