/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.tx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import akka.actor.ActorSystem;
import akka.actor.Status.Failure;
import akka.actor.Status.Success;
import akka.dispatch.Futures;
import akka.pattern.AskTimeoutException;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
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
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import scala.concurrent.Promise;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public class ProxyReadWriteTransactionTest {
    private static final FiniteDuration EXP_NO_MESSAGE_TIMEOUT = Duration.apply(300, TimeUnit.MILLISECONDS);
    private static final RemoteDeviceId DEVICE_ID =
            new RemoteDeviceId("dev1", InetSocketAddress.createUnresolved("localhost", 17830));
    private static final YangInstanceIdentifier PATH = YangInstanceIdentifier.of();
    private static final LogicalDatastoreType STORE = LogicalDatastoreType.CONFIGURATION;

    private static ActorSystem system = ActorSystem.apply();
    private TestProbe masterActor;
    private ContainerNode node;

    @Before
    public void setUp() {
        masterActor = new TestProbe(system);
        node = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("", "cont")))
                .build();
    }

    @AfterClass
    public static void staticTearDown() {
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
    public void testCancel() {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        tx.cancel();
        masterActor.expectMsgClass(CancelRequest.class);
        masterActor.reply(Boolean.TRUE);
    }

    @Test
    public void testCommit() throws InterruptedException, ExecutionException, TimeoutException {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();
        commit(tx);
    }

    @Test
    public void testCommitAfterCancel() throws InterruptedException, ExecutionException, TimeoutException {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();
        commit(tx);
        assertFalse(tx.cancel());
    }

    @Test
    public void testDoubleCommit() throws InterruptedException, ExecutionException, TimeoutException {
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
    public void testDelete() {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        tx.delete(STORE, PATH);
        final DeleteRequest deleteRequest = masterActor.expectMsgClass(DeleteRequest.class);
        assertEquals(STORE, deleteRequest.getStore());
        assertEquals(PATH, deleteRequest.getPath());
    }

    @Test
    public void testDeleteAfterCommit() throws InterruptedException, ExecutionException, TimeoutException {
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
    public void testPut() {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        tx.put(STORE, PATH, node);
        final PutRequest putRequest = masterActor.expectMsgClass(PutRequest.class);
        assertEquals(STORE, putRequest.getStore());
        assertEquals(PATH, putRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, putRequest.getNormalizedNodeMessage().getNode());
    }

    @Test
    public void testPutAfterCommit() throws InterruptedException, ExecutionException, TimeoutException {
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
    public void testMerge() {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        tx.merge(STORE, PATH, node);
        final MergeRequest mergeRequest = masterActor.expectMsgClass(MergeRequest.class);
        assertEquals(STORE, mergeRequest.getStore());
        assertEquals(PATH, mergeRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, mergeRequest.getNormalizedNodeMessage().getNode());
    }

    @Test
    public void testMergeAfterCommit() throws InterruptedException, ExecutionException, TimeoutException {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        commit(tx);
        try {
            tx.merge(STORE, PATH, node);
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            masterActor.expectNoMessage(EXP_NO_MESSAGE_TIMEOUT);
        }
    }

    private void commit(final ProxyReadWriteTransaction tx)
            throws InterruptedException, ExecutionException, TimeoutException {
        final ListenableFuture<?> submit = tx.commit();
        masterActor.expectMsgClass(SubmitRequest.class);
        masterActor.reply(new Success(null));
        submit.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testRead() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        final ListenableFuture<Optional<NormalizedNode>> read = tx.read(STORE, PATH);
        final ReadRequest readRequest = masterActor.expectMsgClass(ReadRequest.class);
        assertEquals(STORE, readRequest.getStore());
        assertEquals(PATH, readRequest.getPath());

        masterActor.reply(new NormalizedNodeMessage(PATH, node));
        assertEquals(Optional.of(node), read.get(5, TimeUnit.SECONDS));
    }

    @Test
    public void testReadEmpty() throws Exception {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx();

        final ListenableFuture<Optional<NormalizedNode>> read = tx.read(STORE, PATH);
        masterActor.expectMsgClass(ReadRequest.class);
        masterActor.reply(new EmptyReadResponse());
        final Optional<NormalizedNode> result = read.get(5, TimeUnit.SECONDS);
        assertFalse(result.isPresent());
    }

    @Test
    public void testReadFailure() throws InterruptedException, TimeoutException {
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
            assertTrue("Unexpected cause " + cause, cause instanceof ReadFailedException);
            assertEquals(mockEx, cause.getCause());
        }
    }

    @Test
    public void testExists() throws Exception {
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
    public void testExistsFailure() throws InterruptedException, TimeoutException {
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
            assertTrue("Unexpected cause " + cause, cause instanceof ReadFailedException);
            assertEquals(mockEx, cause.getCause());
        }
    }

    @Test
    public void testFutureOperationsWithMasterDown() throws InterruptedException, TimeoutException {
        ProxyReadWriteTransaction tx = newSuccessfulProxyTx(Timeout.apply(500, TimeUnit.MILLISECONDS));

        ListenableFuture<?> future = tx.read(STORE, PATH);
        masterActor.expectMsgClass(ReadRequest.class);

        // master doesn't reply
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue("Unexpected cause " + cause, cause instanceof ReadFailedException);
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
            assertTrue("Unexpected cause " + cause, cause instanceof ReadFailedException);
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
            assertTrue("Unexpected cause " + cause, cause instanceof TransactionCommitFailedException);
            verifyDocumentedException(cause.getCause());
        }
    }

    @Test
    public void testDelayedMasterActorFuture() throws InterruptedException, TimeoutException, ExecutionException {
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
    public void testFailedMasterActorFuture() throws InterruptedException, TimeoutException {
        final AskTimeoutException mockEx = new AskTimeoutException("mock");
        ProxyReadWriteTransaction tx = new ProxyReadWriteTransaction(DEVICE_ID, Futures.failed(mockEx),
                system.dispatcher(), Timeout.apply(5, TimeUnit.SECONDS));

        ListenableFuture<?> future = tx.read(STORE, PATH);
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue("Unexpected cause " + cause, cause instanceof ReadFailedException);
            assertEquals(mockEx, cause.getCause());
        }

        future = tx.exists(STORE, PATH);
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue("Unexpected cause " + cause, cause instanceof ReadFailedException);
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
            assertTrue("Unexpected cause " + cause, cause instanceof TransactionCommitFailedException);
            assertEquals(mockEx, cause.getCause());
        }
    }

    private static void verifyDocumentedException(final Throwable cause) {
        assertTrue("Unexpected cause " + cause, cause instanceof DocumentedException);
        final DocumentedException de = (DocumentedException) cause;
        assertEquals(ErrorSeverity.WARNING, de.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_FAILED, de.getErrorTag());
        assertEquals(ErrorType.APPLICATION, de.getErrorType());
    }
}
