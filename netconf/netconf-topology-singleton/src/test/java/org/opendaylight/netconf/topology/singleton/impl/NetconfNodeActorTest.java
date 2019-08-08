/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
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
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY;

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
import com.google.common.collect.Lists;
import com.google.common.io.ByteSource;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.opendaylight.controller.cluster.schema.provider.impl.YangTextSchemaSourceSerializationProxy;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice.SchemaResourcesDTO;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
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
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceFilter;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.TextToASTTransformer;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class NetconfNodeActorTest {

    private static final Timeout TIMEOUT = new Timeout(Duration.create(5, "seconds"));
    private static final RevisionSourceIdentifier SOURCE_IDENTIFIER1 = RevisionSourceIdentifier.create("yang1");
    private static final RevisionSourceIdentifier SOURCE_IDENTIFIER2 = RevisionSourceIdentifier.create("yang2");

    private ActorSystem system = ActorSystem.create();
    private final TestKit testKit = new TestKit(system);

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private ActorRef masterRef;
    private RemoteDeviceId remoteDeviceId;
    private final SharedSchemaRepository masterSchemaRepository = new SharedSchemaRepository("master");

    @Mock
    private DOMRpcService mockDOMRpcService;

    @Mock
    private DOMMountPointService mockMountPointService;

    @Mock
    private DOMMountPointService.DOMMountPointBuilder mockMountPointBuilder;

    @Mock
    private ObjectRegistration<DOMMountPoint> mockMountPointReg;

    @Mock
    private DOMDataBroker mockDOMDataBroker;

    @Mock
    private SchemaSourceRegistration<?> mockSchemaSourceReg1;

    @Mock
    private SchemaSourceRegistration<?> mockSchemaSourceReg2;

    @Mock
    private SchemaSourceRegistry mockRegistry;

    @Mock
    private SchemaContextFactory mockSchemaContextFactory;

    @Mock
    private SchemaRepository mockSchemaRepository;

    @Mock
    private SchemaContext mockSchemaContext;

    @Mock
    private SchemaResourcesDTO schemaResourceDTO;

    @Before
    public void setup() {
        initMocks(this);

        remoteDeviceId = new RemoteDeviceId("netconf-topology",
                new InetSocketAddress(InetAddresses.forString("127.0.0.1"), 9999));

        masterSchemaRepository.registerSchemaSourceListener(
                TextToASTTransformer.create(masterSchemaRepository, masterSchemaRepository));

        doReturn(masterSchemaRepository).when(schemaResourceDTO).getSchemaRepository();
        doReturn(mockRegistry).when(schemaResourceDTO).getSchemaRegistry();
        final NetconfTopologySetup setup = NetconfTopologySetupBuilder.create().setActorSystem(system)
                .setIdleTimeout(Duration.apply(1, TimeUnit.SECONDS)).setSchemaResourceDTO(schemaResourceDTO).build();

        final Props props = NetconfNodeActor.props(setup, remoteDeviceId, TIMEOUT, mockMountPointService);

        masterRef = TestActorRef.create(system, props, "master_messages");

        resetMountPointMocks();

        doReturn(mockMountPointBuilder).when(mockMountPointService).createMountPoint(any());

        doReturn(mockSchemaSourceReg1).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        doReturn(mockSchemaSourceReg2).when(mockRegistry).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER2));

        doReturn(mockSchemaContextFactory).when(mockSchemaRepository)
                .createSchemaContextFactory(any(SchemaSourceFilter.class));
    }

    @After
    public void teardown() {
        TestKit.shutdownActorSystem(system, true);
        system = null;
    }

    @Test
    public void testInitializeAndRefreshMasterData() {

        // Test CreateInitialMasterActorData.

        initializeMaster(Lists.newArrayList());

        // Test RefreshSetupMasterActorData.

        final RemoteDeviceId newRemoteDeviceId = new RemoteDeviceId("netconf-topology2",
                new InetSocketAddress(InetAddresses.forString("127.0.0.2"), 9999));

        final NetconfTopologySetup newSetup = NetconfTopologySetupBuilder.create()
                .setSchemaResourceDTO(schemaResourceDTO).setActorSystem(system).build();

        masterRef.tell(new RefreshSetupMasterActorData(newSetup, newRemoteDeviceId), testKit.getRef());

        testKit.expectMsgClass(MasterActorDataInitialized.class);
    }

    @Test
    public void tesAskForMasterMountPoint() {

        // Test with master not setup yet.

        final TestKit kit = new TestKit(system);

        masterRef.tell(new AskForMasterMountPoint(kit.getRef()), kit.getRef());

        final Failure failure = kit.expectMsgClass(Failure.class);
        assertTrue(failure.cause() instanceof NotMasterException);

        // Now initialize - master should send the RegisterMountPoint message.

        List<SourceIdentifier> sourceIdentifiers = Lists.newArrayList(RevisionSourceIdentifier.create("testID"));
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

        doReturn(mockSchemaContextFactory).when(mockSchemaRepository)
                .createSchemaContextFactory(any(SchemaSourceFilter.class));

        final SchemaSourceRegistration<?> newMockSchemaSourceReg = mock(SchemaSourceRegistration.class);

        final SchemaContextFactory newMockSchemaContextFactory = mock(SchemaContextFactory.class);
        doReturn(Futures.immediateFuture(mockSchemaContext))
                .when(newMockSchemaContextFactory).createSchemaContext(any());

        doAnswer(unused -> {
            SettableFuture<SchemaContext> future = SettableFuture.create();
            new Thread(() -> {
                doReturn(newMockSchemaSourceReg).when(mockRegistry).registerSchemaSource(any(),
                        withSourceId(SOURCE_IDENTIFIER1));

                doReturn(newMockSchemaContextFactory).when(mockSchemaRepository)
                        .createSchemaContextFactory(any(SchemaSourceFilter.class));

                slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1), masterRef),
                        testKit.getRef());

                future.set(mockSchemaContext);
            }).start();
            return future;
        }).when(mockSchemaContextFactory).createSchemaContext(any());

        doReturn(mockSchemaContextFactory).when(mockSchemaRepository)
                .createSchemaContextFactory(any(SchemaSourceFilter.class));

        slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1), masterRef), testKit.getRef());

        verify(mockMountPointBuilder, timeout(5000)).register();
        verify(mockMountPointBuilder, after(500)).addInitialSchemaContext(mockSchemaContext);
        verify(mockMountPointBuilder).addService(eq(DOMDataBroker.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMRpcService.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMNotificationService.class), any());
        verify(mockSchemaSourceReg1).close();
        verify(mockRegistry, times(2)).registerSchemaSource(any(), withSourceId(SOURCE_IDENTIFIER1));
        verify(mockSchemaRepository, times(2)).createSchemaContextFactory(any(SchemaSourceFilter.class));
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
        final NetconfTopologySetup setup = NetconfTopologySetupBuilder.create().setSchemaResourceDTO(schemaResourceDTO2)
                .setActorSystem(system).build();

        final ActorRef slaveRef = system.actorOf(NetconfNodeActor.props(setup, remoteDeviceId, TIMEOUT,
                mockMountPointService));

        // Test unrecoverable failure.

        doReturn(Futures.immediateFailedFuture(new SchemaResolutionException("mock")))
                .when(mockSchemaContextFactory).createSchemaContext(any());

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
            .when(mockSchemaContextFactory).createSchemaContext(any());

        slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1, SOURCE_IDENTIFIER2),
                masterRef), testKit.getRef());

        testKit.expectMsgClass(Success.class);

        verify(mockMountPointBuilder, timeout(5000)).register();
        verifyNoMoreInteractions(mockSchemaSourceReg1, mockSchemaSourceReg2);

        // Test AskTimeoutException with an interleaved successful registration. The first schema context resolution
        // attempt should not be retried.

        reset(mockSchemaSourceReg1, mockSchemaSourceReg2, mockSchemaRepository, mockSchemaContextFactory);
        resetMountPointMocks();

        final SchemaContextFactory mockSchemaContextFactorySuccess = mock(SchemaContextFactory.class);
        doReturn(Futures.immediateFuture(mockSchemaContext))
                .when(mockSchemaContextFactorySuccess).createSchemaContext(any());

        doAnswer(unused -> {
            SettableFuture<SchemaContext> future = SettableFuture.create();
            new Thread(() -> {
                doReturn(mockSchemaContextFactorySuccess).when(mockSchemaRepository)
                        .createSchemaContextFactory(any(SchemaSourceFilter.class));

                slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1, SOURCE_IDENTIFIER2),
                        masterRef), testKit.getRef());

                future.setException(new SchemaResolutionException("mock", new AskTimeoutException("timeout")));
            }).start();
            return future;
        }).when(mockSchemaContextFactory).createSchemaContext(any());

        doReturn(mockSchemaContextFactory).when(mockSchemaRepository)
                .createSchemaContextFactory(any(SchemaSourceFilter.class));

        slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1, SOURCE_IDENTIFIER2),
                masterRef), testKit.getRef());

        verify(mockMountPointBuilder, timeout(5000)).register();
        verify(mockSchemaRepository, times(2)).createSchemaContextFactory(any(SchemaSourceFilter.class));
    }

    @Test(expected = MissingSchemaSourceException.class)
    public void testMissingSchemaSourceOnMissingProvider() throws Exception {
        SchemaResourcesDTO schemaResourceDTO2 = mock(SchemaResourcesDTO.class);
        doReturn(DEFAULT_SCHEMA_REPOSITORY).when(schemaResourceDTO2).getSchemaRegistry();
        doReturn(DEFAULT_SCHEMA_REPOSITORY).when(schemaResourceDTO2).getSchemaRepository();
        final NetconfTopologySetup setup = NetconfTopologySetupBuilder.create().setActorSystem(system)
                .setSchemaResourceDTO(schemaResourceDTO2).setIdleTimeout(Duration.apply(1, TimeUnit.SECONDS)).build();
        final Props props = NetconfNodeActor.props(setup, remoteDeviceId, TIMEOUT, mockMountPointService);
        ActorRef actor = TestActorRef.create(system, props, "master_messages_2");

        final SourceIdentifier sourceIdentifier = RevisionSourceIdentifier.create("testID");

        final ProxyYangTextSourceProvider proxyYangProvider =
                new ProxyYangTextSourceProvider(actor, system.dispatcher(), TIMEOUT);

        final Future<YangTextSchemaSourceSerializationProxy> resolvedSchemaFuture =
                proxyYangProvider.getYangTextSchemaSource(sourceIdentifier);
        Await.result(resolvedSchemaFuture, TIMEOUT.duration());
    }

    @Test
    public void testYangTextSchemaSourceRequest() throws Exception {
        final SourceIdentifier sourceIdentifier = RevisionSourceIdentifier.create("testID");

        final ProxyYangTextSourceProvider proxyYangProvider =
                new ProxyYangTextSourceProvider(masterRef, system.dispatcher(), TIMEOUT);

        final YangTextSchemaSource yangTextSchemaSource = YangTextSchemaSource.delegateForByteSource(sourceIdentifier,
                ByteSource.wrap("YANG".getBytes(UTF_8)));

        // Test success.

        final SchemaSourceRegistration<YangTextSchemaSource> schemaSourceReg = masterSchemaRepository
                .registerSchemaSource(id -> Futures.immediateFuture(yangTextSchemaSource),
                     PotentialSchemaSource.create(sourceIdentifier, YangTextSchemaSource.class, 1));

        final Future<YangTextSchemaSourceSerializationProxy> resolvedSchemaFuture =
                proxyYangProvider.getYangTextSchemaSource(sourceIdentifier);

        final YangTextSchemaSourceSerializationProxy success = Await.result(resolvedSchemaFuture, TIMEOUT.duration());

        assertEquals(sourceIdentifier, success.getRepresentation().getIdentifier());
        assertEquals("YANG", convertStreamToString(success.getRepresentation().openStream()));

        // Test missing source failure.

        exception.expect(MissingSchemaSourceException.class);

        schemaSourceReg.close();

        final Future<YangTextSchemaSourceSerializationProxy> failedSchemaFuture =
                proxyYangProvider.getYangTextSchemaSource(sourceIdentifier);

        Await.result(failedSchemaFuture, TIMEOUT.duration());
    }

    @Test
    @SuppressWarnings({"checkstyle:AvoidHidingCauseException", "checkstyle:IllegalThrows"})
    public void testSlaveInvokeRpc() throws Throwable {

        final List<SourceIdentifier> sourceIdentifiers =
                Lists.newArrayList(RevisionSourceIdentifier.create("testID"));

        initializeMaster(sourceIdentifiers);
        registerSlaveMountPoint();

        ArgumentCaptor<DOMRpcService> domRPCServiceCaptor = ArgumentCaptor.forClass(DOMRpcService.class);
        verify(mockMountPointBuilder).addService(eq(DOMRpcService.class), domRPCServiceCaptor.capture());

        final DOMRpcService slaveDomRPCService = domRPCServiceCaptor.getValue();
        assertTrue(slaveDomRPCService instanceof ProxyDOMRpcService);

        final QName testQName = QName.create("", "TestQname");
        final SchemaPath schemaPath = SchemaPath.create(true, testQName);
        final NormalizedNode<?, ?> outputNode = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(testQName))
                .withChild(ImmutableNodes.leafNode(testQName, "foo")).build();
        final RpcError rpcError = RpcResultBuilder.newError(RpcError.ErrorType.RPC, null, "Rpc invocation failed.");

        // RPC with no response output.

        doReturn(Futures.immediateCheckedFuture(null)).when(mockDOMRpcService).invokeRpc(any(), any());

        DOMRpcResult result = slaveDomRPCService.invokeRpc(schemaPath, outputNode).get(2, TimeUnit.SECONDS);

        assertEquals(null, result);

        // RPC with response output.

        doReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult(outputNode)))
                .when(mockDOMRpcService).invokeRpc(any(), any());

        result = slaveDomRPCService.invokeRpc(schemaPath, outputNode).get(2, TimeUnit.SECONDS);

        assertEquals(outputNode, result.getResult());
        assertTrue(result.getErrors().isEmpty());

        // RPC with response error.

        doReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult(rpcError)))
                .when(mockDOMRpcService).invokeRpc(any(), any());

        result = slaveDomRPCService.invokeRpc(schemaPath, outputNode).get(2, TimeUnit.SECONDS);

        assertNull(result.getResult());
        assertEquals(rpcError, result.getErrors().iterator().next());

        // RPC with response output and error.

        doReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult(outputNode, rpcError)))
                .when(mockDOMRpcService).invokeRpc(any(), any());

        final DOMRpcResult resultOutputError =
                slaveDomRPCService.invokeRpc(schemaPath, outputNode).get(2, TimeUnit.SECONDS);

        assertEquals(outputNode, resultOutputError.getResult());
        assertEquals(rpcError, resultOutputError.getErrors().iterator().next());

        // RPC failure.

        exception.expect(DOMRpcException.class);

        doReturn(Futures.immediateFailedCheckedFuture(new ClusteringRpcException("mock")))
                .when(mockDOMRpcService).invokeRpc(any(), any());

        try {
            slaveDomRPCService.invokeRpc(schemaPath, outputNode).get(2, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void testSlaveNewTransactionRequests() {

        doReturn(mock(DOMDataReadOnlyTransaction.class)).when(mockDOMDataBroker).newReadOnlyTransaction();
        doReturn(mock(DOMDataReadWriteTransaction.class)).when(mockDOMDataBroker).newReadWriteTransaction();
        doReturn(mock(DOMDataWriteTransaction.class)).when(mockDOMDataBroker).newWriteOnlyTransaction();

        initializeMaster(Collections.emptyList());
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

    private ActorRef registerSlaveMountPoint() {
        SchemaResourcesDTO schemaResourceDTO2 = mock(SchemaResourcesDTO.class);
        doReturn(mockRegistry).when(schemaResourceDTO2).getSchemaRegistry();
        doReturn(mockSchemaRepository).when(schemaResourceDTO2).getSchemaRepository();
        final ActorRef slaveRef = system.actorOf(NetconfNodeActor.props(
                NetconfTopologySetupBuilder.create().setSchemaResourceDTO(schemaResourceDTO2).setActorSystem(system)
                .build(), remoteDeviceId, TIMEOUT, mockMountPointService));

        doReturn(Futures.immediateFuture(mockSchemaContext))
                .when(mockSchemaContextFactory).createSchemaContext(any());

        slaveRef.tell(new RegisterMountPoint(ImmutableList.of(SOURCE_IDENTIFIER1, SOURCE_IDENTIFIER2),
                masterRef), testKit.getRef());

        verify(mockMountPointBuilder, timeout(5000)).register();
        verify(mockMountPointBuilder).addInitialSchemaContext(mockSchemaContext);
        verify(mockMountPointBuilder).addService(eq(DOMDataBroker.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMRpcService.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMNotificationService.class), any());

        testKit.expectMsgClass(Success.class);

        return slaveRef;
    }

    private void initializeMaster(List<SourceIdentifier> sourceIdentifiers) {
        masterRef.tell(new CreateInitialMasterActorData(mockDOMDataBroker, sourceIdentifiers,
                mockDOMRpcService), testKit.getRef());

        testKit.expectMsgClass(MasterActorDataInitialized.class);
    }

    private void resetMountPointMocks() {
        reset(mockMountPointReg, mockMountPointBuilder);

        doNothing().when(mockMountPointReg).close();

        doReturn(mockMountPointBuilder).when(mockMountPointBuilder).addInitialSchemaContext(any());
        doReturn(mockMountPointBuilder).when(mockMountPointBuilder).addService(any(), any());
        doReturn(mockMountPointReg).when(mockMountPointBuilder).register();
    }

    private static PotentialSchemaSource<?> withSourceId(final SourceIdentifier identifier) {
        return argThat(new ArgumentMatcher<PotentialSchemaSource<?>>() {
            @Override
            public boolean matches(final Object argument) {
                final PotentialSchemaSource<?> potentialSchemaSource = (PotentialSchemaSource<?>) argument;
                return identifier.equals(potentialSchemaSource.getSourceIdentifier());
            }
        });
    }

    private static String convertStreamToString(final InputStream is) {
        try (Scanner scanner = new Scanner(is)) {
            return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        }
    }
}
