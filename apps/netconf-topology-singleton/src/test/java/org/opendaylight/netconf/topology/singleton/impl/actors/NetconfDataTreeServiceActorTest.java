/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.util.concurrent.FluentFuture;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status;
import org.apache.pekko.testkit.TestActorRef;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.spi.DataStoreService;
import org.opendaylight.netconf.topology.singleton.impl.netconf.NetconfServiceFailedException;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CommitRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CreateEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.DeleteEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.MergeEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.RemoveEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.ReplaceEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
class NetconfDataTreeServiceActorTest {
    static final YangInstanceIdentifier PATH = YangInstanceIdentifier.of();
    static final LogicalDatastoreType STORE = CONFIGURATION;
    static final Timeout TIMEOUT = Timeout.apply(5, TimeUnit.SECONDS);
    static final ContainerNode NODE = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create("", "cont")))
        .build();
    private static final FluentFuture<DefaultDOMRpcResult> EMPTY_RPC = FluentFutures.immediateFluentFuture(
        new DefaultDOMRpcResult());

    private static ActorSystem system = ActorSystem.apply();

    @Mock
    private DataStoreService dataStoreService;

    private TestProbe probe;
    private ActorRef actorRef;

    @BeforeEach
    void setUp() {
        actorRef = TestActorRef.create(system,
            NetconfDataTreeServiceActor.props(dataStoreService, Duration.ofSeconds(2)));
        probe = TestProbe.apply(system);
    }

    @AfterAll
    static void staticTearDown() {
        TestKit.shutdownActorSystem(system, true);
    }

    @Test
    void testGet() {
        doReturn(immediateFluentFuture(Optional.of(NODE))).when(dataStoreService).get(OPERATIONAL, PATH, List.of());
        actorRef.tell(new GetRequest(OPERATIONAL, PATH, List.of()), probe.ref());

        verify(dataStoreService).get(OPERATIONAL, PATH, List.of());
        final NormalizedNodeMessage response = probe.expectMsgClass(NormalizedNodeMessage.class);
        assertEquals(NODE, response.getNode());
    }

    @Test
    void testGetEmpty() {
        doReturn(immediateFluentFuture(Optional.empty())).when(dataStoreService).get(OPERATIONAL, PATH, List.of());
        actorRef.tell(new GetRequest(OPERATIONAL, PATH, List.of()), probe.ref());

        verify(dataStoreService).get(OPERATIONAL, PATH, List.of());
        probe.expectMsgClass(EmptyReadResponse.class);
    }

    @Test
    void testGetFailure() {
        final ReadFailedException cause = new ReadFailedException("fail");
        doReturn(immediateFailedFluentFuture(cause)).when(dataStoreService).get(OPERATIONAL, PATH, List.of());
        actorRef.tell(new GetRequest(OPERATIONAL, PATH, List.of()), probe.ref());

        verify(dataStoreService).get(OPERATIONAL, PATH, List.of());
        final Status.Failure response = probe.expectMsgClass(Status.Failure.class);
        assertEquals(cause, response.cause());
    }

    @Test
    void testGetConfig() {
        doReturn(immediateFluentFuture(Optional.of(NODE))).when(dataStoreService).get(CONFIGURATION, PATH, List.of());
        actorRef.tell(new GetRequest(CONFIGURATION, PATH, List.of()), probe.ref());

        verify(dataStoreService).get(CONFIGURATION, PATH, List.of());
        final NormalizedNodeMessage response = probe.expectMsgClass(NormalizedNodeMessage.class);
        assertEquals(NODE, response.getNode());
    }

    @Test
    void testGetConfigEmpty() {
        doReturn(immediateFluentFuture(Optional.empty())).when(dataStoreService).get(CONFIGURATION, PATH, List.of());
        actorRef.tell(new GetRequest(CONFIGURATION, PATH, List.of()), probe.ref());

        verify(dataStoreService).get(CONFIGURATION, PATH, List.of());
        probe.expectMsgClass(EmptyReadResponse.class);
    }

    @Test
    void testGetConfigFailure() {
        final ReadFailedException cause = new ReadFailedException("fail");
        doReturn(immediateFailedFluentFuture(cause)).when(dataStoreService).get(CONFIGURATION, PATH, List.of());
        actorRef.tell(new GetRequest(CONFIGURATION, PATH, List.of()), probe.ref());

        verify(dataStoreService).get(CONFIGURATION, PATH, List.of());
        final Status.Failure response = probe.expectMsgClass(Status.Failure.class);
        assertEquals(cause, response.cause());
    }

    @Test
    void testMerge() {
        doReturn(EMPTY_RPC).when(dataStoreService).merge(PATH, NODE);
        final NormalizedNodeMessage node = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new MergeEditConfigRequest(node), probe.ref());
        verify(dataStoreService).merge(PATH, NODE);
    }

    @Test
    void testReplace() {
        doReturn(EMPTY_RPC).when(dataStoreService).replace(PATH, NODE);
        final NormalizedNodeMessage node = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new ReplaceEditConfigRequest(node), probe.ref());
        verify(dataStoreService).replace(PATH, NODE);
    }

    @Test
    void testCreate() {
        doReturn(EMPTY_RPC).when(dataStoreService).create(PATH, NODE);
        final NormalizedNodeMessage node = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new CreateEditConfigRequest(node), probe.ref());
        verify(dataStoreService).create(PATH, NODE);
    }

    @Test
    void testDelete() {
        doReturn(EMPTY_RPC).when(dataStoreService).delete(PATH);
        actorRef.tell(new DeleteEditConfigRequest(PATH), probe.ref());
        verify(dataStoreService).delete(PATH);
    }

    @Test
    void testRemove() {
        doReturn(EMPTY_RPC).when(dataStoreService).remove(PATH);
        actorRef.tell(new RemoveEditConfigRequest(PATH), probe.ref());
        verify(dataStoreService).remove(PATH);
    }

    @Test
    void testCommit() {
        doReturn(EMPTY_RPC).when(dataStoreService).commit();
        actorRef.tell(new CommitRequest(), probe.ref());

        verify(dataStoreService).commit();
        probe.expectMsgClass(InvokeRpcMessageReply.class);
    }

    @Test
    void testCommitFail() {
        final RpcError rpcError = RpcResultBuilder.newError(ErrorType.APPLICATION, new ErrorTag("fail"), "fail");
        final TransactionCommitFailedException failure = new TransactionCommitFailedException("fail", rpcError);
        final NetconfServiceFailedException cause = new NetconfServiceFailedException(
            String.format("%s: Commit of operation failed", 1), failure);
        when(dataStoreService.commit()).thenReturn(FluentFutures.immediateFailedFluentFuture(cause));
        actorRef.tell(new CommitRequest(), probe.ref());

        verify(dataStoreService).commit();
        final Status.Failure response = probe.expectMsgClass(Status.Failure.class);
        assertEquals(cause, response.cause());
    }

    @Test
    void testIdleTimeout() {
        final TestProbe testProbe = new TestProbe(system);
        testProbe.watch(actorRef);
        doReturn(EMPTY_RPC).when(dataStoreService).cancel();
        verify(dataStoreService, timeout(3000)).cancel();
        testProbe.expectTerminated(actorRef, TIMEOUT.duration());
    }
}
