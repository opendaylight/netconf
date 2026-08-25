/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.netconf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;

import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
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
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

class ProxyNetconfServiceTest {
    private static final RemoteDeviceId DEVICE_ID =
        new RemoteDeviceId("dev1", InetSocketAddress.createUnresolved("localhost", 17830));
    private static final YangInstanceIdentifier PATH = YangInstanceIdentifier.of();
    private static final ActorSystem SYSTEM = ActorSystem.apply();

    private TestProbe masterActor;
    private ContainerNode node;

    @BeforeEach
    void setUp() {
        masterActor = new TestProbe(SYSTEM);
        node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("", "cont")))
            .build();
    }

    @AfterAll
    static void staticTearDown() {
        TestKit.shutdownActorSystem(SYSTEM, true);
    }

    private ProxyNetconfService newSuccessfulProxyNetconfService() {
        return new ProxyNetconfService(DEVICE_ID, CompletableFuture.completedStage(masterActor.ref()),
            Duration.ofSeconds(5));
    }

    private ProxyNetconfService newSuccessfulProxyNetconfService(final Duration timeout) {
        return new ProxyNetconfService(DEVICE_ID, CompletableFuture.completedStage(masterActor.ref()), timeout);
    }

    @Test
    void testGet() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final ListenableFuture<Optional<NormalizedNode>> get = netconf.get(OPERATIONAL, PATH, List.of());
        final GetRequest getRequest = masterActor.expectMsgClass(GetRequest.class);
        assertEquals(PATH, getRequest.path());

        masterActor.reply(new NormalizedNodeMessage(PATH, node));
        assertEquals(Optional.of(node), get.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testGetFailure() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();

        final ListenableFuture<Optional<NormalizedNode>> get = netconf.get(OPERATIONAL, PATH, List.of());
        masterActor.expectMsgClass(GetRequest.class);
        final RuntimeException mockEx = new RuntimeException("fail");
        masterActor.reply(new Status.Failure(mockEx));

        final var cause = assertThrows(ExecutionException.class, () -> get.get(5, TimeUnit.SECONDS)).getCause();
        assertInstanceOf(ReadFailedException.class, cause, "Unexpected cause " + cause);
        assertEquals(mockEx, cause.getCause());
    }

    @Test
    void testGetEmpty() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final ListenableFuture<Optional<NormalizedNode>> get = netconf.get(OPERATIONAL, PATH, List.of());
        masterActor.expectMsgClass(GetRequest.class);
        masterActor.reply(new EmptyReadResponse());
        final Optional<NormalizedNode> result = get.get(5, TimeUnit.SECONDS);
        assertFalse(result.isPresent());
    }

    @Test
    void testGetConfigEmpty() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final ListenableFuture<Optional<NormalizedNode>> getConfig = netconf.get(CONFIGURATION, PATH, List.of());
        masterActor.expectMsgClass(GetRequest.class);
        masterActor.reply(new EmptyReadResponse());
        final Optional<NormalizedNode> result = getConfig.get(5, TimeUnit.SECONDS);
        assertFalse(result.isPresent());
    }

    @Test
    void testMerge() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final var future = netconf.merge(PATH, node);
        final MergeEditConfigRequest mergeRequest = masterActor.expectMsgClass(MergeEditConfigRequest.class);
        assertEquals(PATH, mergeRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, mergeRequest.getNormalizedNodeMessage().getNode());
        assertSuccess(future);
    }

    @Test
    void testReplace() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final var future = netconf.replace(PATH, node);
        final ReplaceEditConfigRequest replaceRequest = masterActor.expectMsgClass(ReplaceEditConfigRequest.class);
        assertEquals(PATH, replaceRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, replaceRequest.getNormalizedNodeMessage().getNode());
        assertSuccess(future);
    }

    @Test
    void testCreate() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final var future = netconf.create(PATH, node);
        final CreateEditConfigRequest createRequest = masterActor.expectMsgClass(CreateEditConfigRequest.class);
        assertEquals(PATH, createRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, createRequest.getNormalizedNodeMessage().getNode());
        assertSuccess(future);
    }

    @Test
    void testDelete() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final var future = netconf.delete(PATH);
        final DeleteEditConfigRequest deleteRequest = masterActor.expectMsgClass(DeleteEditConfigRequest.class);
        assertEquals(PATH, deleteRequest.getPath());
        assertSuccess(future);
    }

    @Test
    void testRemove() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final var future = netconf.remove(PATH);
        final RemoveEditConfigRequest removeRequest = masterActor.expectMsgClass(RemoveEditConfigRequest.class);
        assertEquals(PATH, removeRequest.getPath());
        assertSuccess(future);
    }

    @Test
    void testEditDeviceFailure() {
        final var netconf = newSuccessfulProxyNetconfService();
        final var future = netconf.create(PATH, node);
        masterActor.expectMsgClass(CreateEditConfigRequest.class);
        final var deviceError = new NetconfDocumentedException("Lock denied", ErrorType.PROTOCOL, ErrorTag.LOCK_DENIED,
            ErrorSeverity.ERROR);
        masterActor.reply(new Status.Failure(deviceError));

        // Passed through as is, so that it is reported with the device's error tag
        final var cause = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS)).getCause();
        assertEquals(deviceError, cause);
    }

    @Test
    void testEditFailure() {
        final var netconf = newSuccessfulProxyNetconfService();
        final var future = netconf.create(PATH, node);
        masterActor.expectMsgClass(CreateEditConfigRequest.class);
        final var failure = new IllegalStateException("fail");
        masterActor.reply(new Status.Failure(failure));

        final var cause = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS)).getCause();
        assertInstanceOf(NetconfServiceFailedException.class, cause, "Unexpected cause " + cause);
        assertEquals(DEVICE_ID + ": Create operation failed", cause.getMessage());
        assertEquals(failure, cause.getCause());
    }

    @Test
    void testCommitEmptyResult() throws Exception {
        final var netconf = newSuccessfulProxyNetconfService();
        final var future = netconf.commit();
        masterActor.expectMsgClass(CommitRequest.class);
        masterActor.reply(new EmptyResultResponse());
        assertEquals(List.of(), future.get(5, TimeUnit.SECONDS).errors());
    }

    @Test
    void testCommit() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        commit(netconf);
    }

    @Test
    void testFutureOperationsWithMasterDown() {
        final var netconf = newSuccessfulProxyNetconfService(Duration.ofMillis(500));

        final var future = netconf.get(OPERATIONAL, PATH, List.of());
        masterActor.expectMsgClass(GetRequest.class);
        final var cause = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS)).getCause();
        assertInstanceOf(ReadFailedException.class, cause, "Unexpected cause " + cause);
        verifyDocumentedException(cause.getCause());

        final var configFuture = netconf.get(CONFIGURATION, PATH, List.of());
        masterActor.expectMsgClass(GetRequest.class);

        final var configCause =
            assertThrows(ExecutionException.class, () -> configFuture.get(5, TimeUnit.SECONDS)).getCause();
        assertInstanceOf(ReadFailedException.class, configCause, "Unexpected cause " + configCause);
        verifyDocumentedException(configCause.getCause());

        final var commitFuture = netconf.commit();
        masterActor.expectMsgClass(CommitRequest.class);
        final var commitCause =
            assertThrows(ExecutionException.class, () -> commitFuture.get(5, TimeUnit.SECONDS)).getCause();
        assertInstanceOf(NetconfServiceFailedException.class, commitCause, "Unexpected cause " + commitCause);
        verifyDocumentedException(commitCause.getCause());
    }

    private void commit(final ProxyNetconfService netconf) throws Exception {
        final ListenableFuture<?> submit = netconf.commit();
        masterActor.expectMsgClass(CommitRequest.class);
        masterActor.reply(new InvokeRpcMessageReply(null, List.of()));
        submit.get(5, TimeUnit.SECONDS);
    }

    private void assertSuccess(final ListenableFuture<? extends DOMRpcResult> future) throws Exception {
        // Nothing may report success before the master has actually processed the request
        assertFalse(future.isDone());
        masterActor.reply(new InvokeRpcMessageReply(null, List.of()));
        assertEquals(List.of(), future.get(5, TimeUnit.SECONDS).errors());
    }

    private static void verifyDocumentedException(final Throwable cause) {
        final var de = assertInstanceOf(DocumentedException.class, cause, "Unexpected cause " + cause);
        assertEquals(ErrorSeverity.WARNING, de.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_FAILED, de.getErrorTag());
        assertEquals(ErrorType.APPLICATION, de.getErrorType());
    }
}
