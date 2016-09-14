/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainClosedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;

public class TxChainTest {

    @Mock
    private DOMDataBroker broker;
    @Mock
    private TransactionChainListener listener;
    @Mock
    private DOMDataReadOnlyTransaction readOnlyTx;
    @Mock
    private AbstractWriteTx writeOnlyTx;
    @Mock
    private AutoCloseable registration;
    private final ArgumentCaptor<TxListener> captor = ArgumentCaptor.forClass(TxListener.class);
    private TxChain chain;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(readOnlyTx).when(broker).newReadOnlyTransaction();
        doReturn(writeOnlyTx).when(broker).newWriteOnlyTransaction();
        doReturn(registration).when(writeOnlyTx).addListener(any());
        chain = new TxChain(broker, listener);
    }

    @Test()
    public void testNewReadOnlyTransactionPrevSubmitted() throws Exception {
        chain.newWriteOnlyTransaction();
        verify(writeOnlyTx).addListener(captor.capture());
        captor.getValue().onTransactionSubmitted(writeOnlyTx);
        chain.newReadOnlyTransaction();
    }

    @Test(expected = IllegalStateException.class)
    public void testNewReadOnlyTransactionPrevNotSubmitted() throws Exception {
        chain.newWriteOnlyTransaction();
        chain.newReadOnlyTransaction();
    }

    @Test
    public void testNewReadWriteTransactionPrevSubmitted() throws Exception {
        chain.newReadWriteTransaction();
        verify(writeOnlyTx).addListener(captor.capture());
        captor.getValue().onTransactionSubmitted(writeOnlyTx);
        chain.newReadWriteTransaction();
    }

    @Test(expected = IllegalStateException.class)
    public void testNewReadWriteTransactionPrevNotSubmitted() throws Exception {
        chain.newReadWriteTransaction();
        chain.newReadWriteTransaction();
    }

    @Test
    public void testNewWriteOnlyTransactionPrevSubmitted() throws Exception {
        chain.newWriteOnlyTransaction();
        verify(writeOnlyTx).addListener(captor.capture());
        captor.getValue().onTransactionSubmitted(writeOnlyTx);
        chain.newWriteOnlyTransaction();
    }

    @Test(expected = IllegalStateException.class)
    public void testNewWriteOnlyTransactionPrevNotSubmitted() throws Exception {
        chain.newWriteOnlyTransaction();
        chain.newWriteOnlyTransaction();
    }

    @Test(expected = TransactionChainClosedException.class)
    public void testCloseAfterFinished() throws Exception {
        chain.close();
        verify(listener).onTransactionChainSuccessful(chain);
        chain.newReadOnlyTransaction();
    }

    @Test
    public void testChainFail() throws Exception {
        final AbstractWriteTx writeTx = chain.newWriteOnlyTransaction();
        final ArgumentCaptor<TxListener> captor = ArgumentCaptor.forClass(TxListener.class);
        verify(writeOnlyTx).addListener(captor.capture());
        writeTx.submit();
        final TransactionCommitFailedException cause = new TransactionCommitFailedException("fail");
        captor.getValue().onTransactionFailed(writeOnlyTx, cause);
        verify(registration).close();
        verify(listener).onTransactionChainFailed(chain, writeOnlyTx, cause);
    }

    @Test
    public void testChainSuccess() throws Exception {
        final AbstractWriteTx writeTx = chain.newWriteOnlyTransaction();
        chain.close();
        final ArgumentCaptor<TxListener> captor = ArgumentCaptor.forClass(TxListener.class);
        verify(writeOnlyTx).addListener(captor.capture());
        writeTx.submit();
        captor.getValue().onTransactionSuccessful(writeOnlyTx);
        verify(registration).close();
        verify(listener).onTransactionChainSuccessful(chain);
    }

    @Test
    public void testCancel() throws Exception {
        final AbstractWriteTx writeTx = chain.newWriteOnlyTransaction();
        verify(writeOnlyTx).addListener(captor.capture());
        writeTx.cancel();
        captor.getValue().onTransactionCancelled(writeOnlyTx);
        chain.newWriteOnlyTransaction();
    }
}