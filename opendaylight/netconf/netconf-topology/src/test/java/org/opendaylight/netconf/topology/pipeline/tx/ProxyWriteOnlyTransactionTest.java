/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.pipeline.tx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import akka.actor.ActorSystem;
import akka.dispatch.ExecutionContexts;
import akka.dispatch.Futures;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netconf.topology.pipeline.ProxyNetconfDeviceDataBroker;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class ProxyWriteOnlyTransactionTest {
    private static final YangInstanceIdentifier path = YangInstanceIdentifier.create();
    private ArgumentCaptor<NormalizedNodeMessage> nodeMessageArgumentCaptor;

    @Mock
    private ProxyNetconfDeviceDataBroker mockedDelegate;

    @Mock
    private ActorSystem mockedActorSystem;

    @Mock
    private NormalizedNode<?, ?> normalizedNode;

    private ProxyWriteOnlyTransaction tx;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mockedActorSystem.dispatcher()).thenReturn(ExecutionContexts.fromExecutorService(MoreExecutors.newDirectExecutorService()));

        nodeMessageArgumentCaptor = ArgumentCaptor.forClass(NormalizedNodeMessage.class);
        tx = new ProxyWriteOnlyTransaction(mockedActorSystem, mockedDelegate);
    }

    @Test
    public void testPut() {
        doNothing().when(mockedDelegate).put(any(LogicalDatastoreType.class), any(NormalizedNodeMessage.class));
        tx.put(LogicalDatastoreType.OPERATIONAL, path, normalizedNode);
        verify(mockedDelegate).put(eq(LogicalDatastoreType.OPERATIONAL), nodeMessageArgumentCaptor.capture());
        assertEquals(path, nodeMessageArgumentCaptor.getValue().getIdentifier());
        assertEquals(normalizedNode, nodeMessageArgumentCaptor.getValue().getNode());
    }

    @Test
    public void testMerge() {
        doNothing().when(mockedDelegate).merge(any(LogicalDatastoreType.class), any(NormalizedNodeMessage.class));
        tx.merge(LogicalDatastoreType.CONFIGURATION, path, normalizedNode);
        verify(mockedDelegate).merge(eq(LogicalDatastoreType.CONFIGURATION), nodeMessageArgumentCaptor.capture());
        assertEquals(path, nodeMessageArgumentCaptor.getValue().getIdentifier());
        assertEquals(normalizedNode, nodeMessageArgumentCaptor.getValue().getNode());
    }

    @Test
    public void testCancel() {
        when(mockedDelegate.cancel()).thenReturn(true);
        assertTrue(tx.cancel());
        verify(mockedDelegate).cancel();
    }

    @Test
    public void testDelete() {
        doNothing().when(mockedDelegate).delete(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class));
        tx.delete(LogicalDatastoreType.OPERATIONAL, path);
        verify(mockedDelegate).delete(eq(LogicalDatastoreType.OPERATIONAL), eq(path));
    }

    @Test
    public void testSuccessfulSubmit() throws Exception {
        when(mockedDelegate.submit()).thenReturn(Futures.<Void>successful(null));
        CheckedFuture submitFuture = tx.submit();
        verify(mockedDelegate).submit();
        assertTrue(submitFuture.isDone());
        assertEquals(submitFuture.checkedGet(), null);
    }

    @Test
    public void testFailedSubmit() {
        when(mockedDelegate.submit()).thenReturn(Futures.<Void>failed(new TransactionCommitFailedException("fail")));
        CheckedFuture submitFuture = tx.submit();
        verify(mockedDelegate).submit();
        assertTrue(submitFuture.isDone());
        try {
            submitFuture.checkedGet();
            fail("Exception expected");
        } catch(Exception e) {
            assertTrue(e instanceof TransactionCommitFailedException);
        }
    }

    @Test
    public void testSuccessfulCommit() throws ExecutionException, InterruptedException {
        RpcResult<TransactionStatus> rpcResult = mock(RpcResult.class);
        when(mockedDelegate.commit()).thenReturn(Futures.successful(rpcResult));
        ListenableFuture<RpcResult<TransactionStatus>> submitFuture = tx.commit();
        verify(mockedDelegate).commit();
        assertTrue(submitFuture.isDone());
        assertEquals(submitFuture.get(), rpcResult);
    }

    @Test
    public void testFailedCommit() {
        when(mockedDelegate.commit()).thenReturn(Futures.<RpcResult<TransactionStatus>>failed(new TransactionCommitFailedException("faile")));
        ListenableFuture<RpcResult<TransactionStatus>> submitFuture = tx.commit();
        verify(mockedDelegate).commit();
        assertTrue(submitFuture.isDone());
        try {
            submitFuture.get();
            fail("Exception expected");
        } catch(Exception e) {
            assertTrue(e.getCause() instanceof TransactionCommitFailedException);
        }
    }
}