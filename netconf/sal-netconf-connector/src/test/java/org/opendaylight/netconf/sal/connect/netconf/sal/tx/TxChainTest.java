/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private AbstractWriteTx writeOnlyTx1;
    @Mock
    private AbstractWriteTx writeOnlyTx2;
    @Mock
    private AbstractWriteTx writeOnlyTx3;
    @Mock
    private AutoCloseable registration1;
    @Mock
    private AutoCloseable registration2;
    @Mock
    private AutoCloseable registration3;
    private final ArgumentCaptor<TxListener> captor = ArgumentCaptor.forClass(TxListener.class);
    private TxChain chain;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(broker.newReadOnlyTransaction()).thenReturn(readOnlyTx);
        when(broker.newWriteOnlyTransaction()).thenReturn(writeOnlyTx1)
                .thenReturn(writeOnlyTx2).thenReturn(writeOnlyTx3);
        when(writeOnlyTx1.addListener(any())).thenReturn(registration1);
        when(writeOnlyTx2.addListener(any())).thenReturn(registration2);
        when(writeOnlyTx3.addListener(any())).thenReturn(registration3);
        chain = new TxChain(broker, listener);
    }

    @Test()
    public void testNewReadOnlyTransactionPrevSubmitted() throws Exception {
        chain.newWriteOnlyTransaction();
        verify(writeOnlyTx1).addListener(captor.capture());
        captor.getValue().onTransactionSubmitted(writeOnlyTx1);
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
        verify(writeOnlyTx1).addListener(captor.capture());
        captor.getValue().onTransactionSubmitted(writeOnlyTx1);
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
        verify(writeOnlyTx1).addListener(captor.capture());
        captor.getValue().onTransactionSubmitted(writeOnlyTx1);
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
        verify(writeOnlyTx1).addListener(captor.capture());
        writeTx.submit();
        final TransactionCommitFailedException cause = new TransactionCommitFailedException("fail");
        captor.getValue().onTransactionFailed(writeOnlyTx1, cause);
        verify(registration1).close();
        verify(listener).onTransactionChainFailed(chain, writeOnlyTx1, cause);
    }

    @Test
    public void testChainSuccess() throws Exception {
        final AbstractWriteTx writeTx = chain.newWriteOnlyTransaction();
        chain.close();
        verify(writeOnlyTx1).addListener(captor.capture());
        writeTx.submit();
        captor.getValue().onTransactionSuccessful(writeOnlyTx1);
        verify(registration1).close();
        verify(listener).onTransactionChainSuccessful(chain);
    }

    @Test
    public void testCancel() throws Exception {
        final AbstractWriteTx writeTx = chain.newWriteOnlyTransaction();
        verify(writeOnlyTx1).addListener(captor.capture());
        writeTx.cancel();
        captor.getValue().onTransactionCancelled(writeOnlyTx1);
        chain.newWriteOnlyTransaction();
    }

    @Test
    public void testMultiplePendingTransactions() throws Exception {
        //create 1st tx
        final AbstractWriteTx writeTx1 = chain.newWriteOnlyTransaction();
        final ArgumentCaptor<TxListener> captor1 = ArgumentCaptor.forClass(TxListener.class);
        verify(writeOnlyTx1).addListener(captor1.capture());
        //submit 1st tx
        writeTx1.submit();
        captor1.getValue().onTransactionSubmitted(writeOnlyTx1);

        //create 2nd tx
        final AbstractWriteTx writeTx2 = chain.newWriteOnlyTransaction();
        final ArgumentCaptor<TxListener> captor2 = ArgumentCaptor.forClass(TxListener.class);
        verify(writeTx2).addListener(captor2.capture());
        //submit 2nd tx
        writeTx2.submit();
        captor2.getValue().onTransactionSubmitted(writeOnlyTx2);

        //create 3rd tx
        final AbstractWriteTx writeTx3 = chain.newWriteOnlyTransaction();
        final ArgumentCaptor<TxListener> captor3 = ArgumentCaptor.forClass(TxListener.class);
        verify(writeTx3).addListener(captor3.capture());
        //cancel 3rd tx
        writeTx3.cancel();
        captor3.getValue().onTransactionCancelled(writeOnlyTx3);

        //close chain
        chain.close();

        //complete first two transactions successfully
        captor1.getValue().onTransactionSuccessful(writeOnlyTx1);
        captor2.getValue().onTransactionSuccessful(writeOnlyTx2);

        verify(registration1).close();
        verify(registration2).close();
        verify(registration3).close();
        verify(listener).onTransactionChainSuccessful(chain);
    }

    @Test
    public void testMultiplePendingTransactionsFail() throws Exception {
        //create 1st tx
        final AbstractWriteTx writeTx1 = chain.newWriteOnlyTransaction();
        final ArgumentCaptor<TxListener> captor1 = ArgumentCaptor.forClass(TxListener.class);
        verify(writeOnlyTx1).addListener(captor1.capture());
        //submit 1st tx
        writeTx1.submit();
        captor1.getValue().onTransactionSubmitted(writeOnlyTx1);

        //create 2nd tx
        final AbstractWriteTx writeTx2 = chain.newWriteOnlyTransaction();
        final ArgumentCaptor<TxListener> captor2 = ArgumentCaptor.forClass(TxListener.class);
        verify(writeTx2).addListener(captor2.capture());
        //submit 2nd tx
        writeTx2.submit();
        captor2.getValue().onTransactionSubmitted(writeOnlyTx2);

        //create 3rd tx
        final AbstractWriteTx writeTx3 = chain.newWriteOnlyTransaction();
        final ArgumentCaptor<TxListener> captor3 = ArgumentCaptor.forClass(TxListener.class);
        verify(writeTx3).addListener(captor3.capture());

        chain.close();

        //fail 1st transaction
        final Exception cause1 = new Exception("fail");
        captor1.getValue().onTransactionFailed(writeOnlyTx1, cause1);
        //current unsubmitted transaction should be cancelled
        verify(writeTx3).cancel();
        captor3.getValue().onTransactionCancelled(writeTx3);
        //2nd transaction success
        captor2.getValue().onTransactionSuccessful(writeOnlyTx2);

        verify(registration1).close();
        verify(registration2).close();
        verify(registration3).close();
        verify(listener).onTransactionChainFailed(chain, writeOnlyTx1, cause1);
        // 1 transaction failed, onTransactionChainSuccessful must not be called
        verify(listener, never()).onTransactionChainSuccessful(chain);
    }
}
