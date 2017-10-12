/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.util.Timeout;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.opendaylight.controller.cluster.schema.provider.impl.YangTextSchemaSourceSerializationProxy;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringRpcException;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.RegisterMountPoint;
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
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceFilter;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class NetconfNodeActorTest {

    private static final Timeout TIMEOUT = new Timeout(Duration.create(5, "seconds"));
    private static ActorSystem system;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private ActorRef masterRef;
    private RemoteDeviceId remoteDeviceId;

    @Mock
    private DOMRpcService domRpcService;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DataBroker dataBroker;

    @Before
    public void setup() {
        initMocks(this);
        remoteDeviceId = new RemoteDeviceId("netconf-topology",
                new InetSocketAddress(InetAddresses.forString("127.0.0.1"), 9999));
        final NetconfTopologySetup setup = mock(NetconfTopologySetup.class);

        final Props props = NetconfNodeActor.props(setup, remoteDeviceId, DEFAULT_SCHEMA_REPOSITORY,
                DEFAULT_SCHEMA_REPOSITORY, TIMEOUT, mountPointService);

        system = ActorSystem.create();

        masterRef = TestActorRef.create(system, props, "master_messages");
    }

    @After
    public void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testInitDataMessages() throws Exception {

        final DOMDataBroker domDataBroker = mock(DOMDataBroker.class);
        final List<SourceIdentifier> sourceIdentifiers = Lists.newArrayList();

        /* Test init master data */

        final Future<Object> initialDataToActor =
                Patterns.ask(masterRef, new CreateInitialMasterActorData(domDataBroker, sourceIdentifiers,
                                domRpcService), TIMEOUT);

        final Object success = Await.result(initialDataToActor, TIMEOUT.duration());
        assertTrue(success instanceof MasterActorDataInitialized);


        /* Test refresh master data */

        final RemoteDeviceId remoteDeviceId2 = new RemoteDeviceId("netconf-topology2",
                new InetSocketAddress(InetAddresses.forString("127.0.0.2"), 9999));

        final NetconfTopologySetup setup2 = mock(NetconfTopologySetup.class);

        final Future<Object> refreshDataToActor =
                Patterns.ask(masterRef, new RefreshSetupMasterActorData(setup2, remoteDeviceId2),
                        TIMEOUT);

        final Object success2 = Await.result(refreshDataToActor, TIMEOUT.duration());
        assertTrue(success2 instanceof MasterActorDataInitialized);

    }

    @Test
    public void testRegisterMountPointMessage() throws Exception {

        final DOMDataBroker domDataBroker = mock(DOMDataBroker.class);
        final List<SourceIdentifier> sourceIdentifiers =
                Lists.newArrayList(RevisionSourceIdentifier.create("testID"));

        // init master data

        final Future<Object> initialDataToActor =
                Patterns.ask(masterRef, new CreateInitialMasterActorData(domDataBroker, sourceIdentifiers,
                                domRpcService), TIMEOUT);

        final Object successInit = Await.result(initialDataToActor, TIMEOUT.duration());

        assertTrue(successInit instanceof MasterActorDataInitialized);

        // test if slave get right identifiers from master

        final Future<Object> registerMountPointFuture =
                Patterns.ask(masterRef, new AskForMasterMountPoint(),
                        TIMEOUT);

        final RegisterMountPoint success =
                (RegisterMountPoint) Await.result(registerMountPointFuture, TIMEOUT.duration());

        assertEquals(sourceIdentifiers, success.getSourceIndentifiers());

    }

    @Test
    public void testReceiveRegisterMountpoint() throws Exception {
        final NetconfTopologySetup setup = mock(NetconfTopologySetup.class);
        final RevisionSourceIdentifier yang1 = RevisionSourceIdentifier.create("yang1");
        final RevisionSourceIdentifier yang2 = RevisionSourceIdentifier.create("yang2");
        final SchemaSourceRegistry registry = mock(SchemaSourceRegistry.class);
        final SchemaRepository schemaRepository = mock(SchemaRepository.class);
        final SchemaSourceRegistration<?> regYang1 = mock(SchemaSourceRegistration.class);
        final SchemaSourceRegistration<?> regYang2 = mock(SchemaSourceRegistration.class);
        doReturn(regYang1).when(registry).registerSchemaSource(any(), withSourceId(yang1));
        doReturn(regYang2).when(registry).registerSchemaSource(any(), withSourceId(yang2));
        final SchemaContextFactory schemaContextFactory = mock(SchemaContextFactory.class);
        doReturn(schemaContextFactory).when(schemaRepository).createSchemaContextFactory(any(SchemaSourceFilter.class));
        final SettableFuture<SchemaContext> schemaContextFuture = SettableFuture.create();
        final CheckedFuture<SchemaContext, SchemaResolutionException> checkedFuture =
                Futures.makeChecked(schemaContextFuture, e -> new SchemaResolutionException("fail", e));
        doReturn(checkedFuture).when(schemaContextFactory).createSchemaContext(any());
        final ActorRef slaveRef =
                system.actorOf(NetconfNodeActor.props(setup, remoteDeviceId, registry, schemaRepository, TIMEOUT,
                        mountPointService));
        final List<SourceIdentifier> sources = ImmutableList.of(yang1, yang2);
        slaveRef.tell(new RegisterMountPoint(sources), masterRef);

        verify(registry, timeout(1000)).registerSchemaSource(any(), withSourceId(yang1));
        verify(registry, timeout(1000)).registerSchemaSource(any(), withSourceId(yang2));
        //stop actor
        final Future<Boolean> stopFuture = Patterns.gracefulStop(slaveRef, TIMEOUT.duration());
        Await.result(stopFuture, TIMEOUT.duration());
        //provider should be deregistered
        verify(regYang1).close();
        verify(regYang2).close();
    }

    @Test
    public void testYangTextSchemaSourceRequestMessage() throws Exception {
        final SchemaRepository schemaRepository = mock(SchemaRepository.class);
        final SourceIdentifier sourceIdentifier = RevisionSourceIdentifier.create("testID");
        final Props props = NetconfNodeActor.props(mock(NetconfTopologySetup.class), remoteDeviceId,
                DEFAULT_SCHEMA_REPOSITORY, schemaRepository, TIMEOUT, mountPointService);

        final ActorRef actorRefSchemaRepo = TestActorRef.create(system, props, "master_mocked_schema_repository");
        final ActorContext actorContext = mock(ActorContext.class);
        doReturn(system.dispatcher()).when(actorContext).dispatcher();

        final ProxyYangTextSourceProvider proxyYang =
                new ProxyYangTextSourceProvider(actorRefSchemaRepo, actorContext, TIMEOUT);
        // test if asking for source is resolved and sended back

        final YangTextSchemaSource yangTextSchemaSource = new YangTextSchemaSource(sourceIdentifier) {
            @Override
            protected MoreObjects.ToStringHelper addToStringAttributes(
                    final MoreObjects.ToStringHelper toStringHelper) {
                return null;
            }

            @Override
            public InputStream openStream() throws IOException {
                return new ByteArrayInputStream("YANG".getBytes());
            }
        };


        final ListenableFuture<YangTextSchemaSource> result = Futures.immediateFuture(yangTextSchemaSource);

        doReturn(result).when(schemaRepository).getSchemaSource(sourceIdentifier, YangTextSchemaSource.class);

        final Future<YangTextSchemaSourceSerializationProxy> resolvedSchema =
                proxyYang.getYangTextSchemaSource(sourceIdentifier);

        final YangTextSchemaSourceSerializationProxy success = Await.result(resolvedSchema, TIMEOUT.duration());

        assertEquals(sourceIdentifier, success.getRepresentation().getIdentifier());
        assertEquals("YANG", convertStreamToString(success.getRepresentation().openStream()));


        // test if asking for source is missing
        exception.expect(MissingSchemaSourceException.class);

        final SchemaSourceException schemaSourceException =
                new MissingSchemaSourceException("Fail", sourceIdentifier);

        final CheckedFuture<YangTextSchemaSource, SchemaSourceException> resultFail =
                Futures.immediateFailedCheckedFuture(schemaSourceException);

        doReturn(resultFail).when(schemaRepository).getSchemaSource(sourceIdentifier, YangTextSchemaSource.class);

        final Future<YangTextSchemaSourceSerializationProxy> failedSchema =
                proxyYang.getYangTextSchemaSource(sourceIdentifier);

        Await.result(failedSchema, TIMEOUT.duration());

    }

    @Test
    public void testProxyDOMRpcService() throws Exception {

        final DOMDataBroker domDataBroker = mock(DOMDataBroker.class);
        final List<SourceIdentifier> sourceIdentifiers =
                Lists.newArrayList(RevisionSourceIdentifier.create("testID"));

        // init master data

        final Future<Object> initialDataToActor =
                Patterns.ask(masterRef, new CreateInitialMasterActorData(domDataBroker, sourceIdentifiers,
                        domRpcService), TIMEOUT);

        final Object successInit = Await.result(initialDataToActor, TIMEOUT.duration());

        assertTrue(successInit instanceof MasterActorDataInitialized);

        // test if slave get right identifiers from master

        final ProxyDOMRpcService slaveDomRPCService =
                new ProxyDOMRpcService(system, masterRef, remoteDeviceId, TIMEOUT);

        final QName testQName = QName.create("", "TestQname");
        final SchemaPath schemaPath = SchemaPath.create(true, testQName);
        final NormalizedNode<?, ?> outputNode = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(testQName))
                .withChild(ImmutableNodes.leafNode(testQName, "foo")).build();
        final RpcError rpcError = RpcResultBuilder.newError(RpcError.ErrorType.RPC, null, "Rpc invocation failed.");
        // EmptyResultResponse message

        doReturn(Futures.immediateCheckedFuture(null)).when(domRpcService).invokeRpc(any(), any());

        final CheckedFuture<DOMRpcResult, DOMRpcException> resultFutureEmpty =
                slaveDomRPCService.invokeRpc(schemaPath, outputNode);

        final Object resultNull = resultFutureEmpty.checkedGet(2, TimeUnit.SECONDS);

        assertEquals(null, resultNull);

        // InvokeRpcMessageReply message

        doReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult(outputNode)))
                .when(domRpcService).invokeRpc(any(), any());

        final CheckedFuture<DOMRpcResult, DOMRpcException> resultFutureNn =
                slaveDomRPCService.invokeRpc(schemaPath, outputNode);

        final DOMRpcResult resultNn = resultFutureNn.checkedGet(2, TimeUnit.SECONDS);

        assertEquals(outputNode, resultNn.getResult());
        assertTrue(resultNn.getErrors().isEmpty());

        // InvokeRpcMessageReply message only error

        doReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult(rpcError)))
                .when(domRpcService).invokeRpc(any(), any());

        final CheckedFuture<DOMRpcResult, DOMRpcException> resultFutureError =
                slaveDomRPCService.invokeRpc(schemaPath, outputNode);

        final DOMRpcResult resultError = resultFutureError.checkedGet(2, TimeUnit.SECONDS);

        assertNull(resultError.getResult());
        assertEquals(rpcError, resultError.getErrors().iterator().next());

        // InvokeRpcMessageReply message error + result

        doReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult(outputNode, rpcError)))
                .when(domRpcService).invokeRpc(any(), any());

        final CheckedFuture<DOMRpcResult, DOMRpcException> resultFutureOutputError =
                slaveDomRPCService.invokeRpc(schemaPath, outputNode);

        final DOMRpcResult resultOutputError = resultFutureOutputError.checkedGet(2, TimeUnit.SECONDS);

        assertEquals(outputNode, resultOutputError.getResult());
        assertEquals(rpcError, resultOutputError.getErrors().iterator().next());

        // Throwable message

        exception.expect(DOMRpcException.class);

        doReturn(Futures.immediateFailedCheckedFuture(new ClusteringRpcException("")))
                .when(domRpcService).invokeRpc(any(), any());

        final CheckedFuture<DOMRpcResult, DOMRpcException> resultFutureThrowable =
                slaveDomRPCService.invokeRpc(schemaPath, outputNode);

        resultFutureThrowable.checkedGet(2, TimeUnit.SECONDS);

    }

    private PotentialSchemaSource<?> withSourceId(final SourceIdentifier identifier) {
        return argThat(new ArgumentMatcher<PotentialSchemaSource>() {
            @Override
            public boolean matches(final Object argument) {
                final PotentialSchemaSource potentialSchemaSource = (PotentialSchemaSource) argument;
                return identifier.equals(potentialSchemaSource.getSourceIdentifier());
            }
        });
    }

    private String convertStreamToString(final java.io.InputStream is) {
        final java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

}
