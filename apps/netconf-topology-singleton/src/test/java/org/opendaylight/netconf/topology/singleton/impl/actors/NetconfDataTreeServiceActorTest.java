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
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.time.Duration;
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
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.topology.singleton.impl.netconf.NetconfServiceFailedException;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CommitRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CreateEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.DeleteEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.LockRequest;
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
    static final LogicalDatastoreType STORE = LogicalDatastoreType.CONFIGURATION;
    static final Timeout TIMEOUT = Timeout.apply(5, TimeUnit.SECONDS);
    static final ContainerNode NODE = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create("", "cont")))
        .build();

    private static ActorSystem system = ActorSystem.apply();

    @Mock
    private NetconfDataTreeService netconfService;

    private TestProbe probe;
    private ActorRef actorRef;

    @BeforeEach
    void setUp() {
        actorRef = TestActorRef.create(system,
            NetconfDataTreeServiceActor.props(netconfService, Duration.ofSeconds(2)));
        probe = TestProbe.apply(system);
    }

    @AfterAll
    static void staticTearDown() {
        TestKit.shutdownActorSystem(system, true);
    }

    @Test
    void testGet() {
        doReturn(immediateFluentFuture(Optional.of(NODE))).when(netconfService).get(PATH);
        actorRef.tell(new GetRequest(PATH), probe.ref());

        verify(netconfService).get(PATH);
        final NormalizedNodeMessage response = probe.expectMsgClass(NormalizedNodeMessage.class);
        assertEquals(NODE, response.getNode());
    }

    @Test
    void testGetEmpty() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).get(PATH);
        actorRef.tell(new GetRequest(PATH), probe.ref());

        verify(netconfService).get(PATH);
        probe.expectMsgClass(EmptyReadResponse.class);
    }

    @Test
    void testGetFailure() {
        final ReadFailedException cause = new ReadFailedException("fail");
        doReturn(immediateFailedFluentFuture(cause)).when(netconfService).get(PATH);
        actorRef.tell(new GetRequest(PATH), probe.ref());

        verify(netconfService).get(PATH);
        final Status.Failure response = probe.expectMsgClass(Status.Failure.class);
        assertEquals(cause, response.cause());
    }

    @Test
    void testGetConfig() {
        doReturn(immediateFluentFuture(Optional.of(NODE))).when(netconfService).getConfig(PATH);
        actorRef.tell(new GetConfigRequest(PATH), probe.ref());

        verify(netconfService).getConfig(PATH);
        final NormalizedNodeMessage response = probe.expectMsgClass(NormalizedNodeMessage.class);
        assertEquals(NODE, response.getNode());
    }

    @Test
    void testGetConfigEmpty() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(PATH);
        actorRef.tell(new GetConfigRequest(PATH), probe.ref());

        verify(netconfService).getConfig(PATH);
        probe.expectMsgClass(EmptyReadResponse.class);
    }

    @Test
    void testGetConfigFailure() {
        final ReadFailedException cause = new ReadFailedException("fail");
        doReturn(immediateFailedFluentFuture(cause)).when(netconfService).getConfig(PATH);
        actorRef.tell(new GetConfigRequest(PATH), probe.ref());

        verify(netconfService).getConfig(PATH);
        final Status.Failure response = probe.expectMsgClass(Status.Failure.class);
        assertEquals(cause, response.cause());
    }

    @Test
    void testLock() {
        final ListenableFuture<? extends DOMRpcResult> future = Futures.immediateFuture(new DefaultDOMRpcResult());
        doReturn(future).when(netconfService).lock();
        actorRef.tell(new LockRequest(), probe.ref());
        verify(netconfService).lock();
    }

    @Test
    void testMerge() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(netconfService)
            .merge(STORE, PATH, NODE, Optional.empty());
        final NormalizedNodeMessage node = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new MergeEditConfigRequest(STORE, node, null), probe.ref());
        verify(netconfService).merge(STORE, PATH, NODE, Optional.empty());
    }

    @Test
    void testReplace() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(STORE, PATH, NODE, Optional.empty());
        final NormalizedNodeMessage node = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new ReplaceEditConfigRequest(STORE, node, null), probe.ref());
        verify(netconfService).replace(STORE, PATH, NODE, Optional.empty());
    }

    @Test
    void testCreate() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(netconfService)
            .create(STORE, PATH, NODE, Optional.empty());
        final NormalizedNodeMessage node = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new CreateEditConfigRequest(STORE, node, null), probe.ref());
        verify(netconfService).create(STORE, PATH, NODE, Optional.empty());
    }

    @Test
    void testDelete() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(STORE, PATH);
        actorRef.tell(new DeleteEditConfigRequest(STORE, PATH), probe.ref());
        verify(netconfService).delete(STORE, PATH);
    }

    @Test
    void testRemove() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(netconfService)
            .remove(STORE, PATH);
        actorRef.tell(new RemoveEditConfigRequest(STORE, PATH), probe.ref());
        verify(netconfService).remove(STORE, PATH);
    }

    @Test
    void testCommit() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        actorRef.tell(new CommitRequest(), probe.ref());

        verify(netconfService).commit();
        probe.expectMsgClass(InvokeRpcMessageReply.class);
    }

    @Test
    void testCommitFail() {
        final RpcError rpcError = RpcResultBuilder.newError(ErrorType.APPLICATION, new ErrorTag("fail"), "fail");
        final TransactionCommitFailedException failure = new TransactionCommitFailedException("fail", rpcError);
        final NetconfServiceFailedException cause = new NetconfServiceFailedException(
            String.format("%s: Commit of operation failed", 1), failure);
        when(netconfService.commit()).thenReturn(FluentFutures.immediateFailedFluentFuture(cause));
        actorRef.tell(new CommitRequest(), probe.ref());

        verify(netconfService).commit();
        final Status.Failure response = probe.expectMsgClass(Status.Failure.class);
        assertEquals(cause, response.cause());
    }

    @Test
    void testIdleTimeout() {
        final TestProbe testProbe = new TestProbe(system);
        testProbe.watch(actorRef);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).discardChanges();
        verify(netconfService, timeout(3000)).discardChanges();
        verify(netconfService, timeout(3000)).unlock();
        testProbe.expectTerminated(actorRef, TIMEOUT.duration());
    }
}
