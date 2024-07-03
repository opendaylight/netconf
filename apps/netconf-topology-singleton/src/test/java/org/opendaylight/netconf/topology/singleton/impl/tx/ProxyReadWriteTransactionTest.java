/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.tx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status.Failure;
import org.apache.pekko.actor.Status.Success;
import org.apache.pekko.dispatch.Futures;
import org.apache.pekko.pattern.AskTimeoutException;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import scala.concurrent.Promise;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

class ProxyReadWriteTransactionTest {
    private static final FiniteDuration EXP_NO_MESSAGE_TIMEOUT = Duration.apply(300, TimeUnit.MILLISECONDS);
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

    private ProxyReadWriteTransaction newSuccessfulProxyTx() {
        return newSuccessfulProxyTx(Timeout.apply(5, TimeUnit.SECONDS));
    }

    private ProxyReadWriteTransaction newSuccessfulProxyTx(final Timeout timeout) {
        return new ProxyReadWriteTransaction(DEVICE_ID, Futures.successful(masterActor.ref()),
                system.dispatcher(), timeout);
    }

    @Test
    void testCancel() {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        tx.cancel();
        masterActor.expectMsgClass(CancelRequest.class);
        masterActor.reply(Boolean.TRUE);
    }

    @Test
    void testCommit() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();
        commit(tx);
    }

    @Test
    void testCommitAfterCancel() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();
        commit(tx);
        assertFalse(tx.cancel());
    }

    @Test
    void testDoubleCommit() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        commit(tx);
        try {
            tx.commit();
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            masterActor.expectNoMessage(EXP_NO_MESSAGE_TIMEOUT);
        }
    }

    @Test
    void testDelete() {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        tx.delete(STORE, PATH);
        final DeleteRequest deleteRequest = masterActor.expectMsgClass(DeleteRequest.class);
        assertEquals(STORE, deleteRequest.getStore());
        assertEquals(PATH, deleteRequest.getPath());
    }

    @Test
    void testDeleteAfterCommit() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        commit(tx);
        try {
            tx.delete(STORE, PATH);
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            masterActor.expectNoMessage(EXP_NO_MESSAGE_TIMEOUT);
        }
    }

    @Test
    void testPut() {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        tx.put(STORE, PATH, node);
        final PutRequest putRequest = masterActor.expectMsgClass(PutRequest.class);
        assertEquals(STORE, putRequest.getStore());
        assertEquals(PATH, putRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, putRequest.getNormalizedNodeMessage().getNode());
    }

    @Test
    void testPutAfterCommit() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        commit(tx);
        try {
            tx.put(STORE, PATH, node);
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            masterActor.expectNoMessage(EXP_NO_MESSAGE_TIMEOUT);
        }
    }

    @Test
    void testMerge() {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        tx.merge(STORE, PATH, node);
        final MergeRequest mergeRequest = masterActor.expectMsgClass(MergeRequest.class);
        assertEquals(STORE, mergeRequest.getStore());
        assertEquals(PATH, mergeRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, mergeRequest.getNormalizedNodeMessage().getNode());
    }

    @Test
    void testMergeAfterCommit() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        commit(tx);
        try {
            tx.merge(STORE, PATH, node);
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            masterActor.expectNoMessage(EXP_NO_MESSAGE_TIMEOUT);
        }
    }

    private void commit(final ProxyReadWriteTransaction tx) throws Exception {
        final ListenableFuture<?> submit = tx.commit();
        masterActor.expectMsgClass(SubmitRequest.class);
        masterActor.reply(new Success(null));
        submit.get(5, TimeUnit.SECONDS);
    }

    @Test
    void testRead() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        final ListenableFuture<Optional<NormalizedNode>> read = tx.read(STORE, PATH);
        final ReadRequest readRequest = masterActor.expectMsgClass(ReadRequest.class);
        assertEquals(STORE, readRequest.getStore());
        assertEquals(PATH, readRequest.getPath());

        masterActor.reply(new NormalizedNodeMessage(PATH, node));
        assertEquals(Optional.of(node), read.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testReadEmpty() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        final ListenableFuture<Optional<NormalizedNode>> read = tx.read(STORE, PATH);
        masterActor.expectMsgClass(ReadRequest.class);
        masterActor.reply(new EmptyReadResponse());
        final Optional<NormalizedNode> result = read.get(5, TimeUnit.SECONDS);
        assertFalse(result.isPresent());
    }

    @Test
    void testReadFailure() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        final ListenableFuture<Optional<NormalizedNode>> read = tx.read(STORE, PATH);
        masterActor.expectMsgClass(ReadRequest.class);
        final RuntimeException mockEx = new RuntimeException("fail");
        masterActor.reply(new Failure(mockEx));

        try {
            read.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertInstanceOf(ReadFailedException.class, cause, "Unexpected cause " + cause);
            assertEquals(mockEx, cause.getCause());
        }
    }

    @Test
    void testExists() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        final ListenableFuture<Boolean> read = tx.exists(STORE, PATH);
        final ExistsRequest existsRequest = masterActor.expectMsgClass(ExistsRequest.class);
        assertEquals(STORE, existsRequest.getStore());
        assertEquals(PATH, existsRequest.getPath());

        masterActor.reply(Boolean.TRUE);
        final Boolean result = read.get(5, TimeUnit.SECONDS);
        assertTrue(result);
    }

    @Test
    void testExistsFailure() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        final ListenableFuture<Boolean> read = tx.exists(STORE, PATH);
        masterActor.expectMsgClass(ExistsRequest.class);
        final RuntimeException mockEx = new RuntimeException("fail");
        masterActor.reply(new Failure(mockEx));

        try {
            read.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertInstanceOf(ReadFailedException.class, cause, "Unexpected cause " + cause);
            assertEquals(mockEx, cause.getCause());
        }
    }

    @Test
    void testFutureOperationsWithMasterDown() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx(Timeout.apply(500, TimeUnit.MILLISECONDS));

        ListenableFuture<?> future = tx.read(STORE, PATH);
        masterActor.expectMsgClass(ReadRequest.class);

        // master doesn't reply
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertInstanceOf(ReadFailedException.class, cause, "Unexpected cause " + cause);
            verifyDocumentedException(cause.getCause());
        }

        future = tx.exists(STORE, PATH);
        masterActor.expectMsgClass(ExistsRequest.class);

        // master doesn't reply
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertInstanceOf(ReadFailedException.class, cause, "Unexpected cause " + cause);
            verifyDocumentedException(cause.getCause());
        }

        future = tx.commit();
        masterActor.expectMsgClass(SubmitRequest.class);

        // master doesn't reply
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertInstanceOf(TransactionCommitFailedException.class, cause, "Unexpected cause " + cause);
            verifyDocumentedException(cause.getCause());
        }
    }

    @Test
    void testDelayedMasterActorFuture() throws Exception {
        final Promise<Object> promise = Futures.promise();
        ProxyReadWriteTransaction tx = new ProxyReadWriteTransaction(DEVICE_ID, promise.future(),
                system.dispatcher(), Timeout.apply(5, TimeUnit.SECONDS));

        final ListenableFuture<Optional<NormalizedNode>> read = tx.read(STORE, PATH);
        final ListenableFuture<Boolean> exists = tx.exists(STORE, PATH);

        tx.put(STORE, PATH, node);
        tx.merge(STORE, PATH, node);
        tx.delete(STORE, PATH);

        final ListenableFuture<?> commit = tx.commit();

        promise.success(masterActor.ref());

        masterActor.expectMsgClass(ReadRequest.class);
        masterActor.reply(new NormalizedNodeMessage(PATH, node));

        masterActor.expectMsgClass(ExistsRequest.class);
        masterActor.reply(Boolean.TRUE);

        masterActor.expectMsgClass(PutRequest.class);
        masterActor.expectMsgClass(MergeRequest.class);
        masterActor.expectMsgClass(DeleteRequest.class);

        masterActor.expectMsgClass(SubmitRequest.class);
        masterActor.reply(new Success(null));

        read.get(5, TimeUnit.SECONDS).isPresent();
        assertTrue(exists.get(5, TimeUnit.SECONDS));
        commit.get(5, TimeUnit.SECONDS);
    }

    @Test
    void testFailedMasterActorFuture() throws Exception {
        final AskTimeoutException mockEx = new AskTimeoutException("mock");
        ProxyReadWriteTransaction tx = new ProxyReadWriteTransaction(DEVICE_ID, Futures.failed(mockEx),
                system.dispatcher(), Timeout.apply(5, TimeUnit.SECONDS));

        ListenableFuture<?> future = tx.read(STORE, PATH);
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertInstanceOf(ReadFailedException.class, cause, "Unexpected cause " + cause);
            assertEquals(mockEx, cause.getCause());
        }

        future = tx.exists(STORE, PATH);
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertInstanceOf(ReadFailedException.class, cause, "Unexpected cause " + cause);
            assertEquals(mockEx, cause.getCause());
        }

        tx.put(STORE, PATH, node);
        tx.merge(STORE, PATH, node);
        tx.delete(STORE, PATH);

        future = tx.commit();
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertInstanceOf(TransactionCommitFailedException.class, cause, "Unexpected cause " + cause);
            assertEquals(mockEx, cause.getCause());
        }
    }

    private static void verifyDocumentedException(final Throwable cause) {
        assertInstanceOf(DocumentedException.class, cause, "Unexpected cause " + cause);
        final DocumentedException de = (DocumentedException) cause;
        assertEquals(ErrorSeverity.WARNING, de.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_FAILED, de.getErrorTag());
        assertEquals(ErrorType.APPLICATION, de.getErrorType());
    }
}
