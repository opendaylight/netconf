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

import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status;
import org.apache.pekko.dispatch.Futures;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CommitRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CreateEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.DeleteEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.DiscardChangesRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.LockRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.MergeEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.RemoveEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.ReplaceEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.UnlockRequest;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
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
    private static final LogicalDatastoreType STORE = LogicalDatastoreType.CONFIGURATION;

    private static ActorSystem system = ActorSystem.apply();
    private TestProbe masterActor;
    private ContainerNode node;

    @BeforeEach
    void setUp() {
        masterActor = new TestProbe(system);
        node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("", "cont")))
            .build();
    }

    @AfterAll
    static void staticTearDown() {
        TestKit.shutdownActorSystem(system, true);
    }

    private ProxyNetconfService newSuccessfulProxyNetconfService() {
        return new ProxyNetconfService(DEVICE_ID, Futures.successful(masterActor.ref()),
            system.dispatcher(), Timeout.apply(5, TimeUnit.SECONDS));
    }

    private ProxyNetconfService newSuccessfulProxyNetconfService(final Timeout timeout) {
        return new ProxyNetconfService(DEVICE_ID, Futures.successful(masterActor.ref()),
            system.dispatcher(), timeout);
    }

    @Test
    void testLock() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.lock();
        masterActor.expectMsgClass(LockRequest.class);
    }

    @Test
    void testUnlock() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.unlock();
        masterActor.expectMsgClass(UnlockRequest.class);
    }

    @Test
    void testDiscardChanges() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.discardChanges();
        masterActor.expectMsgClass(DiscardChangesRequest.class);
    }

    @Test
    void testGet() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final ListenableFuture<Optional<NormalizedNode>> get = netconf.get(PATH);
        final GetRequest getRequest = masterActor.expectMsgClass(GetRequest.class);
        assertEquals(PATH, getRequest.getPath());

        masterActor.reply(new NormalizedNodeMessage(PATH, node));
        assertEquals(Optional.of(node), get.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testGetFailure() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();

        final ListenableFuture<Optional<NormalizedNode>> get = netconf.get(PATH);
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
        final ListenableFuture<Optional<NormalizedNode>> get = netconf.get(PATH);
        masterActor.expectMsgClass(GetRequest.class);
        masterActor.reply(new EmptyReadResponse());
        final Optional<NormalizedNode> result = get.get(5, TimeUnit.SECONDS);
        assertFalse(result.isPresent());
    }

    @Test
    void testGetConfig() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final ListenableFuture<Optional<NormalizedNode>> getConfig = netconf.getConfig(PATH);
        final GetConfigRequest getRequest = masterActor.expectMsgClass(GetConfigRequest.class);
        assertEquals(PATH, getRequest.getPath());

        masterActor.reply(new NormalizedNodeMessage(PATH, node));
        assertEquals(Optional.of(node), getConfig.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testGetConfigFailure() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();

        final ListenableFuture<Optional<NormalizedNode>> getConfig = netconf.getConfig(PATH);
        masterActor.expectMsgClass(GetConfigRequest.class);
        final RuntimeException mockEx = new RuntimeException("fail");
        masterActor.reply(new Status.Failure(mockEx));

        final var cause = assertThrows(ExecutionException.class, () -> getConfig.get(5, TimeUnit.SECONDS)).getCause();
        assertInstanceOf(ReadFailedException.class, cause, "Unexpected cause " + cause);
        assertEquals(mockEx, cause.getCause());
    }

    @Test
    void testGetConfigEmpty() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final ListenableFuture<Optional<NormalizedNode>> getConfig = netconf.getConfig(PATH);
        masterActor.expectMsgClass(GetConfigRequest.class);
        masterActor.reply(new EmptyReadResponse());
        final Optional<NormalizedNode> result = getConfig.get(5, TimeUnit.SECONDS);
        assertFalse(result.isPresent());
    }

    @Test
    void testMerge() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.merge(STORE, PATH, node, Optional.empty());
        final MergeEditConfigRequest mergeRequest = masterActor.expectMsgClass(MergeEditConfigRequest.class);
        assertEquals(STORE, mergeRequest.getStore());
        assertEquals(PATH, mergeRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, mergeRequest.getNormalizedNodeMessage().getNode());
    }

    @Test
    void testReplace() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.replace(STORE, PATH, node, Optional.empty());
        final ReplaceEditConfigRequest replaceRequest = masterActor.expectMsgClass(ReplaceEditConfigRequest.class);
        assertEquals(STORE, replaceRequest.getStore());
        assertEquals(PATH, replaceRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, replaceRequest.getNormalizedNodeMessage().getNode());
    }

    @Test
    void testCreate() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.create(STORE, PATH, node, Optional.empty());
        final CreateEditConfigRequest createRequest = masterActor.expectMsgClass(CreateEditConfigRequest.class);
        assertEquals(STORE, createRequest.getStore());
        assertEquals(PATH, createRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, createRequest.getNormalizedNodeMessage().getNode());
    }

    @Test
    void testDelete() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.delete(STORE, PATH);
        final DeleteEditConfigRequest deleteRequest = masterActor.expectMsgClass(DeleteEditConfigRequest.class);
        assertEquals(STORE, deleteRequest.getStore());
        assertEquals(PATH, deleteRequest.getPath());
    }

    @Test
    void testRemove() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.remove(STORE, PATH);
        final RemoveEditConfigRequest removeRequest = masterActor.expectMsgClass(RemoveEditConfigRequest.class);
        assertEquals(STORE, removeRequest.getStore());
        assertEquals(PATH, removeRequest.getPath());
    }

    @Test
    void testCommit() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        commit(netconf);
    }

    @Test
    void testFutureOperationsWithMasterDown() {
        final var netconf = newSuccessfulProxyNetconfService(Timeout.apply(500, TimeUnit.MILLISECONDS));

        final var future = netconf.get(PATH);
        masterActor.expectMsgClass(GetRequest.class);
        final var cause = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS)).getCause();
        assertInstanceOf(ReadFailedException.class, cause, "Unexpected cause " + cause);
        verifyDocumentedException(cause.getCause());

        final var configFuture = netconf.getConfig(PATH);
        masterActor.expectMsgClass(GetConfigRequest.class);

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

    private static void verifyDocumentedException(final Throwable cause) {
        final var de = assertInstanceOf(DocumentedException.class, cause, "Unexpected cause " + cause);
        assertEquals(ErrorSeverity.WARNING, de.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_FAILED, de.getErrorTag());
        assertEquals(ErrorType.APPLICATION, de.getErrorType());
    }
}
