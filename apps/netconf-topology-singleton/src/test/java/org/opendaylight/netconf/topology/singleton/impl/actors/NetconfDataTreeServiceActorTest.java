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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import java.time.Duration;
import java.util.Optional;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status;
import org.apache.pekko.testkit.TestActorRef;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
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
import org.opendaylight.netconf.client.mdsal.spi.DataOperationService;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.topology.singleton.impl.netconf.NetconfServiceFailedException;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CommitRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CreateEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.DeleteEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.MergeEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.PutEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.RemoveEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.server.api.DataGetParams;
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
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

@ExtendWith(MockitoExtension.class)
class NetconfDataTreeServiceActorTest {
    static final YangInstanceIdentifier PATH = YangInstanceIdentifier.of();
    static final LogicalDatastoreType STORE = LogicalDatastoreType.CONFIGURATION;
    static final ContainerNode NODE = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create("", "cont")))
        .build();
    private static final DataGetParams CONFIG = new DataGetParams(ContentParam.CONFIG, null, null, null);
    private static ActorSystem system = ActorSystem.apply();

    @Mock
    private DataOperationService netconfService;
    @Mock
    private DatabindContext databindContext;
    @Mock
    private EffectiveModelContext context;
    @Mock
    private DataSchemaContext schemaContext;

    private TestProbe probe;
    private ActorRef actorRef;
    private Data dataPath;

    @BeforeEach
    void setUp() {
        actorRef = TestActorRef.create(system,
            NetconfDataTreeServiceActor.props(netconfService, Duration.ofSeconds(2)));
        probe = TestProbe.apply(system);
        dataPath = new Data(databindContext, Inference.of(context), PATH, schemaContext);
    }

    @AfterAll
    static void staticTearDown() {
        TestKit.shutdownActorSystem(system, true);
    }

    @Test
    void testGet() {
        doReturn(immediateFluentFuture(Optional.of(NODE))).when(netconfService).getData(dataPath, CONFIG);
        actorRef.tell(new GetRequest(dataPath, CONFIG), probe.ref());

        verify(netconfService).getData(dataPath, CONFIG);
        final NormalizedNodeMessage response = probe.expectMsgClass(NormalizedNodeMessage.class);
        assertEquals(NODE, response.getNode());
    }

    @Test
    void testGetEmpty() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getData(dataPath, CONFIG);
        actorRef.tell(new GetRequest(dataPath, CONFIG), probe.ref());

        verify(netconfService).getData(dataPath, CONFIG);
        probe.expectMsgClass(EmptyReadResponse.class);
    }

    @Test
    void testGetFailure() {
        final ReadFailedException cause = new ReadFailedException("fail");
        doReturn(immediateFailedFluentFuture(cause)).when(netconfService).getData(dataPath, CONFIG);
        actorRef.tell(new GetRequest(dataPath, CONFIG), probe.ref());

        verify(netconfService).getData(dataPath, CONFIG);
        final Status.Failure response = probe.expectMsgClass(Status.Failure.class);
        assertEquals(cause, response.cause());
    }

    @Test
    void testMerge() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(netconfService)
            .mergeData(PATH, NODE);
        final NormalizedNodeMessage node = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new MergeEditConfigRequest(node), probe.ref());
        verify(netconfService).mergeData(PATH, NODE);
    }

    @Test
    void testReplace() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(netconfService)
            .putData(PATH, NODE);
        final NormalizedNodeMessage node = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new PutEditConfigRequest(node), probe.ref());
        verify(netconfService).putData(PATH, NODE);
    }

    @Test
    void testCreate() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(netconfService)
            .createData(PATH, NODE);
        final NormalizedNodeMessage node = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new CreateEditConfigRequest(node), probe.ref());
        verify(netconfService).createData(PATH, NODE);
    }

    @Test
    void testDelete() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(netconfService)
            .deleteData(dataPath);
        actorRef.tell(new DeleteEditConfigRequest(dataPath), probe.ref());
        verify(netconfService).deleteData(dataPath);
    }

    @Test
    void testRemove() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(netconfService)
            .removeData(dataPath);
        actorRef.tell(new RemoveEditConfigRequest(dataPath), probe.ref());
        verify(netconfService).removeData(dataPath);
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
}
