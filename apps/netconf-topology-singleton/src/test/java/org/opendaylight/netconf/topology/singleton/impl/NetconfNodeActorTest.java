/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.google.common.net.InetAddresses;
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
import java.util.concurrent.TimeoutException;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status.Failure;
import org.apache.pekko.actor.Status.Success;
import org.apache.pekko.pattern.AskTimeoutException;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.testkit.TestActorRef;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMActionException;
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
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Actions;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.spi.DataStoreService;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringActionException;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringRpcException;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.netconf.topology.singleton.messages.NotMasterException;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.RegisterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.UnregisterSlaveMountPoint;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.mdsal.spi.DOMServerStrategy;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.JsonResourceBody;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.util.context.ContainerContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.model.spi.source.DelegatedYangTextSource;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.TextToIRTransformer;
import scala.concurrent.Await;
import scala.concurrent.Future;

@ExtendWith(MockitoExtension.class)
class NetconfNodeActorTest extends AbstractBaseSchemasTest {

    private static final Timeout TIMEOUT = Timeout.create(Duration.ofSeconds(5));
    private static final SourceIdentifier SOURCE_IDENTIFIER1 = new SourceIdentifier("yang1");
    private static final SourceIdentifier SOURCE_IDENTIFIER2 = new SourceIdentifier("yang2");
    private static final ListenableFuture<DefaultDOMRpcResult> EMPTY_RPC = Futures.immediateFuture(
        new DefaultDOMRpcResult());

    private ActorSystem system = ActorSystem.create();
    private final TestKit testKit = new TestKit(system);

    private ActorRef masterRef;
    private RemoteDeviceId remoteDeviceId;
    private final SharedSchemaRepository masterSchemaRepository = new SharedSchemaRepository("master");

    @Mock
    private Rpcs.Normalized mockRpc;
    @Mock
    private DOMRpcService mockDOMRpcService;
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
    private DataStoreService dataStoreService;
    @Mock
    private Registration mockSchemaSourceReg1;
    @Mock
    private Registration mockSchemaSourceReg2;
    @Mock
    private SchemaSourceRegistry mockRegistry;
    @Mock
    private EffectiveModelContextFactory mockSchemaContextFactory;
    @Mock
    private SchemaRepository mockSchemaRepository;
    @Mock
    private EffectiveModelContext mockSchemaContext;
    @Mock
    private DeviceNetconfSchemaProvider deviceSchemaProvider;
    @Mock
    private DatabindContext databindContext;
    @Mock
    private ContainerContext schemaContext;

    @BeforeEach
    void setup() {
        remoteDeviceId = new RemoteDeviceId("netconf-topology",
                new InetSocketAddress(InetAddresses.forString("127.0.0.1"), 9999));

        masterSchemaRepository.registerSchemaSourceListener(
                TextToIRTransformer.create(masterSchemaRepository, masterSchemaRepository));

        final NetconfTopologySetup setup = NetconfTopologySetup.builder()
            .setActorSystem(system)
            .setIdleTimeout(Duration.ofSeconds(1))
            .setDeviceSchemaProvider(deviceSchemaProvider)
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .build();

        final Props props = NetconfNodeActor.props(setup, remoteDeviceId, TIMEOUT, mockMountPointService);

        masterRef = TestActorRef.create(system, props, "master_messages");
    }

    @AfterEach
    void teardown() {
        TestKit.shutdownActorSystem(system, true);
        system = null;
    }

    @Test
    void testInitializeAndRefreshMasterData() {

        // Test CreateInitialMasterActorData.

        initializeMaster(new ArrayList<>());

        // Test RefreshSetupMasterActorData.

        final RemoteDeviceId newRemoteDeviceId = new RemoteDeviceId("netconf-topology2",
                new InetSocketAddress(InetAddresses.forString("127.0.0.2"), 9999));

        final NetconfTopologySetup newSetup = NetconfTopologySetup.builder()
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .setDeviceSchemaProvider(deviceSchemaProvider)
            .setActorSystem(system)
            .build();

        masterRef.tell(new RefreshSetupMasterActorData(newSetup, newRemoteDeviceId), testKit.getRef());

        testKit.expectMsgClass(MasterActorDataInitialized.class);
    }

    @Test
    void testAskForMasterMountPoint() {

        // Test with master not setup yet.

        final TestKit kit = new TestKit(system);

        masterRef.tell(new AskForMasterMountPoint(kit.getRef()), kit.getRef());

        final Failure failure = kit.expectMsgClass(Failure.class);
        assertInstanceOf(NotMasterException.class, failure.cause());

        // Now initialize - master should send the RegisterMountPoint message.

        List<SourceIdentifier> sourceIdentifiers = List.of(new SourceIdentifier("testID"));
        initializeMaster(sourceIdentifiers);

        masterRef.tell(new AskForMasterMountPoint(kit.getRef()), kit.getRef());

        final RegisterMountPoint registerMountPoint = kit.expectMsgClass(RegisterMountPoint.class);

        assertEquals(sourceIdentifiers, registerMountPoint.getSourceIndentifiers());
    }

    @Test
    void testRegisterAndUnregisterMountPoint() throws Exception {
        resetMountPointMocks();
        doReturn(mockMountPointBuilder).when(mockMountPointService).createMountPoint(any());
        doReturn(mockSchemaSourceReg1).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        doReturn(mockSchemaSourceReg2).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER2));
        doReturn(mockSchemaContextFactory).when(mockSchemaRepository).createEffectiveModelContextFactory();

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

        final var newMockSchemaSourceReg = mock(Registration.class);

        final var newMockSchemaContextFactory = mock(EffectiveModelContextFactory.class);
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

    @Test
    void testRegisterMountPointWithSchemaFailures() {
        resetMountPointMocks();
        doReturn(mockMountPointBuilder).when(mockMountPointService).createMountPoint(any());
        doReturn(mockSchemaSourceReg1).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        doReturn(mockSchemaSourceReg2).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER2));
        doReturn(mockSchemaContextFactory).when(mockSchemaRepository).createEffectiveModelContextFactory();

        var deviceSchemaProvider2 = mock(DeviceNetconfSchemaProvider.class);
        doReturn(mockRegistry).when(deviceSchemaProvider2).registry();
        doReturn(mockSchemaRepository).when(deviceSchemaProvider2).repository();
        final NetconfTopologySetup setup = NetconfTopologySetup.builder()
                .setDeviceSchemaProvider(deviceSchemaProvider2)
                .setBaseSchemaProvider(BASE_SCHEMAS)
                .setActorSystem(system)
                .build();

        final ActorRef slaveRef = system.actorOf(NetconfNodeActor.props(setup, remoteDeviceId, TIMEOUT,
                mockMountPointService));

        // Test unrecoverable failure.

        doReturn(Futures.immediateFailedFuture(
            new SchemaResolutionException("mock", new SourceIdentifier("foo"), null)))
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

        doCallRealMethod().when(mockSchemaContext).getQName();
        doReturn(Futures.immediateFailedFuture(new SchemaResolutionException("mock", new SourceIdentifier("foo"),
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

                future.setException(new SchemaResolutionException("mock", new SourceIdentifier("foo"),
                    new AskTimeoutException("timeout")));
            }).start();
            return future;
        }).when(mockSchemaContextFactory).createEffectiveModelContext(anyCollection());

        doReturn(mockSchemaContextFactory).when(mockSchemaRepository).createEffectiveModelContextFactory();

        slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1, SOURCE_IDENTIFIER2),
                masterRef), testKit.getRef());

        verify(mockMountPointBuilder, timeout(5000)).register();
        verify(mockSchemaRepository, times(2)).createEffectiveModelContextFactory();
    }

    @Test
    void testMissingSchemaSourceOnMissingProvider() {
        final var repository = new SharedSchemaRepository("test");

        final var deviceSchemaProvider2 = mock(DeviceNetconfSchemaProvider.class);
        doReturn(repository).when(deviceSchemaProvider2).repository();
        final var setup = NetconfTopologySetup.builder()
            .setActorSystem(system)
            .setDeviceSchemaProvider(deviceSchemaProvider2)
            .setIdleTimeout(Duration.ofSeconds(1))
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .build();
        final var props = NetconfNodeActor.props(setup, remoteDeviceId, TIMEOUT, mockMountPointService);
        final var actor = TestActorRef.create(system, props, "master_messages_2");

        final var sourceIdentifier = new SourceIdentifier("testID");

        final var proxyYangProvider = new ProxyYangTextSourceProvider(actor, system.dispatcher(), TIMEOUT);

        final var resolvedSchemaFuture = proxyYangProvider.getYangTextSchemaSource(sourceIdentifier);
        final var ex = assertThrows(MissingSchemaSourceException.class,
            () -> Await.result(resolvedSchemaFuture, TIMEOUT.duration()));
        assertEquals("No providers registered for source SourceIdentifier [testID]", ex.getMessage());
    }

    @Test
    void testYangTextSchemaSourceRequest() throws Exception {
        doReturn(masterSchemaRepository).when(deviceSchemaProvider).repository();

        final var sourceIdentifier = new SourceIdentifier("testID");

        final var proxyYangProvider = new ProxyYangTextSourceProvider(masterRef, system.dispatcher(), TIMEOUT);

        final var yangTextSchemaSource = new DelegatedYangTextSource(sourceIdentifier, CharSource.wrap("YANG"));

        // Test success.

        try (var schemaSourceReg = masterSchemaRepository.registerSchemaSource(
                id -> Futures.immediateFuture(yangTextSchemaSource),
                PotentialSchemaSource.create(sourceIdentifier, YangTextSource.class, 1))) {
            final var resolvedSchemaFuture = proxyYangProvider.getYangTextSchemaSource(sourceIdentifier);
            final var success = Await.result(resolvedSchemaFuture, TIMEOUT.duration());

            assertEquals(sourceIdentifier, success.getRepresentation().sourceId());
            assertEquals("YANG", success.getRepresentation().read());
        }

        // Test missing source failure.

        final var failedSchemaFuture = proxyYangProvider.getYangTextSchemaSource(sourceIdentifier);
        final var ex = assertThrows(MissingSchemaSourceException.class,
            () -> Await.result(failedSchemaFuture, TIMEOUT.duration()));
        assertThat(ex.getMessage(), startsWith("No providers registered for source"));
        assertThat(ex.getMessage(), containsString(sourceIdentifier.toString()));
    }

    @Test
    void testSlaveInvokeRpc() throws Exception {
        resetMountPointMocks();
        doReturn(mockMountPointBuilder).when(mockMountPointService).createMountPoint(any());
        doReturn(mockSchemaSourceReg1).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        doReturn(mockSchemaSourceReg2).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER2));
        doReturn(mockSchemaContextFactory).when(mockSchemaRepository).createEffectiveModelContextFactory();
        doReturn(mockDOMRpcService).when(mockRpc).domRpcService();

        initializeMaster(List.of(new SourceIdentifier("testID")));
        registerSlaveMountPoint();

        ArgumentCaptor<DOMRpcService> domRPCServiceCaptor = ArgumentCaptor.forClass(DOMRpcService.class);
        verify(mockMountPointBuilder).addService(eq(DOMRpcService.class), domRPCServiceCaptor.capture());

        final DOMRpcService slaveDomRPCService = domRPCServiceCaptor.getValue();
        assertInstanceOf(ProxyDOMRpcService.class, slaveDomRPCService);

        final QName testQName = QName.create("", "TestQname");
        final ContainerNode outputNode = ImmutableNodes.newContainerBuilder()
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
        assertInstanceOf(DOMRpcException.class, cause);
        assertEquals("mock", cause.getMessage());
    }

    @Test
    void testSlaveInvokeAction() throws Exception {
        resetMountPointMocks();
        doReturn(mockMountPointBuilder).when(mockMountPointService).createMountPoint(any());
        doReturn(mockSchemaSourceReg1).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        doReturn(mockSchemaSourceReg2).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER2));
        doReturn(mockSchemaContextFactory).when(mockSchemaRepository).createEffectiveModelContextFactory();
        doReturn(mockDOMRpcService).when(mockRpc).domRpcService();

        initializeMaster(List.of(new SourceIdentifier("testActionID")));
        registerSlaveMountPoint();

        final var domActionServiceCaptor = ArgumentCaptor.forClass(DOMActionService.class);
        verify(mockMountPointBuilder).addService(eq(DOMActionService.class), domActionServiceCaptor.capture());

        final DOMActionService slaveDomActionService = domActionServiceCaptor.getValue();
        assertInstanceOf(ProxyDOMActionService.class, slaveDomActionService);

        final QName testQName = QName.create("test", "2019-08-16", "TestActionQname");
        final Absolute schemaPath = Absolute.of(testQName);

        final YangInstanceIdentifier yangIIdPath = YangInstanceIdentifier.of(testQName);

        final DOMDataTreeIdentifier domDataTreeIdentifier = DOMDataTreeIdentifier.of(OPERATIONAL,
            yangIIdPath);

        final ContainerNode outputNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(testQName))
            .withChild(ImmutableNodes.leafNode(testQName, "foo")).build();

        // Action with no response output.
        doReturn(FluentFutures.immediateNullFluentFuture()).when(mockDOMActionService)
            .invokeAction(any(), any(), any());
        var result = slaveDomActionService.invokeAction(schemaPath, domDataTreeIdentifier, outputNode)
            .get(2, TimeUnit.SECONDS);
        assertEquals(null, result);

        // Action with response output.
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult(outputNode))).when(mockDOMActionService)
            .invokeAction(any(), any(), any());
        result = slaveDomActionService.invokeAction(schemaPath, domDataTreeIdentifier, outputNode)
            .get(2, TimeUnit.SECONDS);

        assertEquals(outputNode, result.value());
        assertTrue(result.errors().isEmpty());

        // Action failure.
        doReturn(FluentFutures.immediateFailedFluentFuture(new ClusteringActionException("mock")))
            .when(mockDOMActionService).invokeAction(any(), any(), any());
        final var future = slaveDomActionService.invokeAction(schemaPath, domDataTreeIdentifier, outputNode);

        final ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        final Throwable cause = e.getCause();
        assertInstanceOf(DOMActionException.class, cause);
        assertEquals("mock", cause.getMessage());
    }

    @Test
    void testSlaveNewTransactionRequests() {
        resetMountPointMocks();
        doReturn(mockMountPointBuilder).when(mockMountPointService).createMountPoint(any());
        doReturn(mockSchemaSourceReg1).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        doReturn(mockSchemaSourceReg2).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER2));
        doReturn(mockSchemaContextFactory).when(mockSchemaRepository).createEffectiveModelContextFactory();
        doReturn(mockDOMRpcService).when(mockRpc).domRpcService();

        doReturn(mock(DOMDataTreeReadTransaction.class)).when(mockDOMDataBroker).newReadOnlyTransaction();
        doReturn(mock(DOMDataTreeReadWriteTransaction.class)).when(mockDOMDataBroker).newReadWriteTransaction();
        doReturn(mock(DOMDataTreeWriteTransaction.class)).when(mockDOMDataBroker).newWriteOnlyTransaction();

        initializeMaster(List.of());
        registerSlaveMountPoint();

        final var domDataBrokerCaptor = ArgumentCaptor.forClass(DOMDataBroker.class);
        verify(mockMountPointBuilder).addService(eq(DOMDataBroker.class), domDataBrokerCaptor.capture());

        final DOMDataBroker slaveDOMDataBroker = domDataBrokerCaptor.getValue();
        assertInstanceOf(ProxyDOMDataBroker.class, slaveDOMDataBroker);

        slaveDOMDataBroker.newReadOnlyTransaction();
        verify(mockDOMDataBroker).newReadOnlyTransaction();

        slaveDOMDataBroker.newReadWriteTransaction();
        verify(mockDOMDataBroker).newReadWriteTransaction();

        slaveDOMDataBroker.newWriteOnlyTransaction();
        verify(mockDOMDataBroker).newWriteOnlyTransaction();
    }

    @Test
    void testSlaveNewNetconfGetDataTreeServiceRequest() {
        // Prepare environment.
        doReturn(mockMountPointBuilder).when(mockMountPointService).createMountPoint(any());
        doReturn(mockSchemaSourceReg1).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        doReturn(mockSchemaSourceReg2).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER2));
        doReturn(mockSchemaContextFactory).when(mockSchemaRepository).createEffectiveModelContextFactory();
        doReturn(mockDOMRpcService).when(mockRpc).domRpcService();

        // Mock called NetconfDataTreeService operations
        final var queryParameters = QueryParameters.of(ContentParam.ALL, DepthParam.of(10));
        final var emptyPath = YangInstanceIdentifier.of();
        doReturn(Futures.immediateFuture(Optional.empty())).when(dataStoreService).get(CONFIGURATION, emptyPath,
            List.of());
        doReturn(Futures.immediateFuture(Optional.empty())).when(dataStoreService).get(OPERATIONAL, emptyPath,
            List.of());

        // Capture registered ServerStrategy instance after successful slave registrations.
        initializeMaster(List.of());
        registerSlaveMountPoint();
        final var netconfCaptor = ArgumentCaptor.forClass(DOMServerStrategy.class);
        verify(mockMountPointBuilder).addService(eq(DOMServerStrategy.class), netconfCaptor.capture());
        final var slaveNetconfService = netconfCaptor.getValue();
        assertInstanceOf(DOMServerStrategy.class, slaveNetconfService);
        final var serverStrategy = slaveNetconfService.serverStrategy();

        // Call Get request on the slave netconf node.
        final var serverRequest = new CompletingServerRequest<DataGetResult>(queryParameters);
        serverStrategy.dataGET(serverRequest);

        // Verify called NetconfDataTreeService operations.
        verify(dataStoreService, timeout(1000)).get(CONFIGURATION, emptyPath, List.of());
        verify(dataStoreService, timeout(1000)).get(OPERATIONAL, emptyPath, List.of());
    }

    @Test
    void testSlaveNewNetconfPUTDataTreeServiceRequest() throws Exception {
        // Prepare environment.
        doReturn(mockMountPointBuilder).when(mockMountPointService).createMountPoint(any());
        doReturn(mockSchemaSourceReg1).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        doReturn(mockSchemaSourceReg2).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER2));
        doReturn(mockSchemaContextFactory).when(mockSchemaRepository).createEffectiveModelContextFactory();
        doReturn(mockDOMRpcService).when(mockRpc).domRpcService();

        // Mock NetconfDataTreeService operations.
        final var emptyPath = YangInstanceIdentifier.of();
        doReturn(EMPTY_RPC).when(dataStoreService).commit();
        doReturn(Futures.immediateFuture(Optional.empty())).when(dataStoreService).get(CONFIGURATION, emptyPath,
            List.of());
        final var contNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create("", "cont")))
            .build();
        doReturn(EMPTY_RPC).when(dataStoreService).replace(emptyPath, contNode);

        // Capture registered ServerStrategy instance after successful slave registrations.
        initializeMaster(List.of());
        registerSlaveMountPoint();
        final var netconfCaptor = ArgumentCaptor.forClass(DOMServerStrategy.class);
        verify(mockMountPointBuilder).addService(eq(DOMServerStrategy.class), netconfCaptor.capture());
        final var slaveNetconfService = netconfCaptor.getValue();
        assertInstanceOf(DOMServerStrategy.class, slaveNetconfService);
        final var serverStrategy = slaveNetconfService.serverStrategy();

        // Call Put request on the slave netconf node.
        final var mockJsonResource = mock(JsonResourceBody.class);
        doReturn(contNode).when(mockJsonResource).toNormalizedNode(any());
        final var serverRequest = new CompletingServerRequest<DataPutResult>();
        serverStrategy.dataPUT(serverRequest, mockJsonResource);
        serverRequest.getResult();

        // Verify NetconfDataTreeService operations.
        verify(dataStoreService, timeout(1000)).get(CONFIGURATION, emptyPath, List.of());
        verify(dataStoreService, timeout(1000)).replace(emptyPath, contNode);
        verify(dataStoreService, timeout(1000)).commit();
    }

    @Test
    void testSlaveNewNetconfFailedPUTDataTreeServiceRequest()
        // Prepare environment.
        throws RequestException, InterruptedException, TimeoutException {
        doReturn(mockMountPointBuilder).when(mockMountPointService).createMountPoint(any());
        doReturn(mockSchemaSourceReg1).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        doReturn(mockSchemaSourceReg2).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER2));
        doReturn(mockSchemaContextFactory).when(mockSchemaRepository).createEffectiveModelContextFactory();
        doReturn(mockDOMRpcService).when(mockRpc).domRpcService();

        // Mock NetconfDataTreeService operations.
        final var emptyPath = YangInstanceIdentifier.of();
        doReturn(EMPTY_RPC).when(dataStoreService).commit();
        // Return Failure when getConfig is called.
        doReturn(Futures.immediateFuture(Optional.empty())).when(dataStoreService).get(CONFIGURATION, emptyPath,
            List.of());
        final var contNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create("", "cont")))
            .build();
        doReturn(Futures.immediateFailedFuture(new Exception("Test failure"))).when(dataStoreService).replace(emptyPath,
            contNode);

        // Capture registered ServerStrategy instance after successful slave registrations.
        initializeMaster(List.of());
        registerSlaveMountPoint();
        final var netconfCaptor = ArgumentCaptor.forClass(DOMServerStrategy.class);
        verify(mockMountPointBuilder).addService(eq(DOMServerStrategy.class), netconfCaptor.capture());
        final var slaveNetconfService = netconfCaptor.getValue();
        assertInstanceOf(DOMServerStrategy.class, slaveNetconfService);
        final var serverStrategy = slaveNetconfService.serverStrategy();

        // Call Put request on the slave netconf node.
        final var serverRequest = new CompletingServerRequest<DataPutResult>();
        final var mockJsonResource = mock(JsonResourceBody.class);
        doReturn(contNode).when(mockJsonResource).toNormalizedNode(any());
        serverStrategy.dataPUT(serverRequest, mockJsonResource);
        // FIXME: Thrown exception is suppressed in ActorProxyNetconfServiceFacade class with createResult method.
        //        See fixme in ActorProxyNetconfServiceFacade class.
        serverRequest.getResult();

        // Verify NetconfDataTreeService operations.
        verify(dataStoreService, timeout(1000)).get(CONFIGURATION, emptyPath, List.of());
        verify(dataStoreService, timeout(1000)).replace(emptyPath, contNode);
        // FIXME: commit should not be called after unsuccessful replace operation.
        verify(dataStoreService, timeout(1000)).commit();
    }

    private ActorRef registerSlaveMountPoint() {
        var deviceSchemaProvider2 = mock(DeviceNetconfSchemaProvider.class);
        doReturn(mockRegistry).when(deviceSchemaProvider2).registry();
        doReturn(mockSchemaRepository).when(deviceSchemaProvider2).repository();
        final ActorRef slaveRef = system.actorOf(NetconfNodeActor.props(NetconfTopologySetup.builder()
                .setDeviceSchemaProvider(deviceSchemaProvider2)
                .setActorSystem(system)
                .setBaseSchemaProvider(BASE_SCHEMAS)
                .build(), remoteDeviceId, TIMEOUT, mockMountPointService));

        doCallRealMethod().when(mockSchemaContext).getQName();
        doReturn(Futures.immediateFuture(mockSchemaContext))
                .when(mockSchemaContextFactory).createEffectiveModelContext(anyCollection());

        slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1, SOURCE_IDENTIFIER2),
                masterRef), testKit.getRef());

        verify(mockMountPointBuilder, timeout(5000)).register();
        verify(mockMountPointBuilder).addService(eq(DOMSchemaService.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMDataBroker.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMServerStrategy.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMRpcService.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMNotificationService.class), any());

        testKit.expectMsgClass(Success.class);
        return slaveRef;
    }

    private void initializeMaster(final List<SourceIdentifier> sourceIdentifiers) {
        masterRef.tell(new CreateInitialMasterActorData(mockDOMDataBroker, dataStoreService, sourceIdentifiers,
                new RemoteDeviceServices(mockRpc, mockDOMActionService)), testKit.getRef());
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
