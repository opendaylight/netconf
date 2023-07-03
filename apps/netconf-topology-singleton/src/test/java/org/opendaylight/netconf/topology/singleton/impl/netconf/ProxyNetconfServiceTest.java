/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.netconf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import akka.actor.ActorSystem;
import akka.actor.Status;
import akka.dispatch.Futures;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
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
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

public class ProxyNetconfServiceTest {
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

    private ProxyNetconfService newSuccessfulProxyNetconfService() {
        return new ProxyNetconfService(DEVICE_ID, Futures.successful(masterActor.ref()),
            system.dispatcher(), Timeout.apply(5, TimeUnit.SECONDS));
    }

    private ProxyNetconfService newSuccessfulProxyNetconfService(final Timeout timeout) {
        return new ProxyNetconfService(DEVICE_ID, Futures.successful(masterActor.ref()),
            system.dispatcher(), timeout);
    }

    @Test
    public void testLock() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.lock();
        masterActor.expectMsgClass(LockRequest.class);
    }

    @Test
    public void testUnlock() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.unlock();
        masterActor.expectMsgClass(UnlockRequest.class);
    }

    @Test
    public void testDiscardChanges() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.discardChanges();
        masterActor.expectMsgClass(DiscardChangesRequest.class);
    }

    @Test
    public void testGet() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final ListenableFuture<Optional<NormalizedNode>> get = netconf.get(PATH);
        final GetRequest getRequest = masterActor.expectMsgClass(GetRequest.class);
        assertEquals(PATH, getRequest.getPath());

        masterActor.reply(new NormalizedNodeMessage(PATH, node));
        assertEquals(Optional.of(node), get.get(5, TimeUnit.SECONDS));
    }

    @Test
    public void testGetFailure() throws InterruptedException, TimeoutException {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();

        final ListenableFuture<Optional<NormalizedNode>> get = netconf.get(PATH);
        masterActor.expectMsgClass(GetRequest.class);
        final RuntimeException mockEx = new RuntimeException("fail");
        masterActor.reply(new Status.Failure(mockEx));

        try {
            get.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue("Unexpected cause " + cause, cause instanceof ReadFailedException);
            assertEquals(mockEx, cause.getCause());
        }
    }

    @Test
    public void testGetEmpty() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final ListenableFuture<Optional<NormalizedNode>> get = netconf.get(PATH);
        masterActor.expectMsgClass(GetRequest.class);
        masterActor.reply(new EmptyReadResponse());
        final Optional<NormalizedNode> result = get.get(5, TimeUnit.SECONDS);
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetConfig() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final ListenableFuture<Optional<NormalizedNode>> getConfig = netconf.getConfig(PATH);
        final GetConfigRequest getRequest = masterActor.expectMsgClass(GetConfigRequest.class);
        assertEquals(PATH, getRequest.getPath());

        masterActor.reply(new NormalizedNodeMessage(PATH, node));
        assertEquals(Optional.of(node), getConfig.get(5, TimeUnit.SECONDS));
    }

    @Test
    public void testGetConfigFailure() throws InterruptedException, TimeoutException {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();

        final ListenableFuture<Optional<NormalizedNode>> getConfig = netconf.getConfig(PATH);
        masterActor.expectMsgClass(GetConfigRequest.class);
        final RuntimeException mockEx = new RuntimeException("fail");
        masterActor.reply(new Status.Failure(mockEx));

        try {
            getConfig.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue("Unexpected cause " + cause, cause instanceof ReadFailedException);
            assertEquals(mockEx, cause.getCause());
        }
    }

    @Test
    public void testGetConfigEmpty() throws Exception {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        final ListenableFuture<Optional<NormalizedNode>> getConfig = netconf.getConfig(PATH);
        masterActor.expectMsgClass(GetConfigRequest.class);
        masterActor.reply(new EmptyReadResponse());
        final Optional<NormalizedNode> result = getConfig.get(5, TimeUnit.SECONDS);
        assertFalse(result.isPresent());
    }

    @Test
    public void testMerge() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.merge(STORE, PATH, node, Optional.empty());
        final MergeEditConfigRequest mergeRequest = masterActor.expectMsgClass(MergeEditConfigRequest.class);
        assertEquals(STORE, mergeRequest.getStore());
        assertEquals(PATH, mergeRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, mergeRequest.getNormalizedNodeMessage().getNode());
    }

    @Test
    public void testReplace() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.replace(STORE, PATH, node, Optional.empty());
        final ReplaceEditConfigRequest replaceRequest = masterActor.expectMsgClass(ReplaceEditConfigRequest.class);
        assertEquals(STORE, replaceRequest.getStore());
        assertEquals(PATH, replaceRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, replaceRequest.getNormalizedNodeMessage().getNode());
    }

    @Test
    public void testCreate() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.create(STORE, PATH, node, Optional.empty());
        final CreateEditConfigRequest createRequest = masterActor.expectMsgClass(CreateEditConfigRequest.class);
        assertEquals(STORE, createRequest.getStore());
        assertEquals(PATH, createRequest.getNormalizedNodeMessage().getIdentifier());
        assertEquals(node, createRequest.getNormalizedNodeMessage().getNode());
    }

    @Test
    public void testDelete() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.delete(STORE, PATH);
        final DeleteEditConfigRequest deleteRequest = masterActor.expectMsgClass(DeleteEditConfigRequest.class);
        assertEquals(STORE, deleteRequest.getStore());
        assertEquals(PATH, deleteRequest.getPath());
    }

    @Test
    public void testRemove() {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        netconf.remove(STORE, PATH);
        final RemoveEditConfigRequest removeRequest = masterActor.expectMsgClass(RemoveEditConfigRequest.class);
        assertEquals(STORE, removeRequest.getStore());
        assertEquals(PATH, removeRequest.getPath());
    }

    @Test
    public void testCommit() throws InterruptedException, ExecutionException, TimeoutException {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService();
        commit(netconf);
    }

    @Test
    public void testFutureOperationsWithMasterDown() throws InterruptedException, TimeoutException {
        ProxyNetconfService netconf = newSuccessfulProxyNetconfService(
            Timeout.apply(500, TimeUnit.MILLISECONDS));

        ListenableFuture<?> future = netconf.get(PATH);
        masterActor.expectMsgClass(GetRequest.class);

        // master doesn't reply
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue("Unexpected cause " + cause, cause instanceof ReadFailedException);
            verifyDocumentedException(cause.getCause());
        }

        future = netconf.getConfig(PATH);
        masterActor.expectMsgClass(GetConfigRequest.class);

        // master doesn't reply
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue("Unexpected cause " + cause, cause instanceof ReadFailedException);
            verifyDocumentedException(cause.getCause());
        }

        future = netconf.commit();
        masterActor.expectMsgClass(CommitRequest.class);

        // master doesn't reply
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Exception should be thrown");
        } catch (final ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue("Unexpected cause " + cause, cause instanceof NetconfServiceFailedException);
            verifyDocumentedException(cause.getCause());
        }
    }

    private void commit(final ProxyNetconfService netconf)
        throws InterruptedException, ExecutionException, TimeoutException {
        final ListenableFuture<?> submit = netconf.commit();
        masterActor.expectMsgClass(CommitRequest.class);
        masterActor.reply(new InvokeRpcMessageReply(null, Collections.emptyList()));
        submit.get(5, TimeUnit.SECONDS);
    }

    private static void verifyDocumentedException(final Throwable cause) {
        assertTrue("Unexpected cause " + cause, cause instanceof DocumentedException);
        final DocumentedException de = (DocumentedException) cause;
        assertEquals(ErrorSeverity.WARNING, de.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_FAILED, de.getErrorTag());
        assertEquals(ErrorType.APPLICATION, de.getErrorType());
    }
}