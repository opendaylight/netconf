/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.FutureCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainClosedException;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Empty;

@ExtendWith(MockitoExtension.class)
class TxChainTest {
    @Mock
    private DOMDataBroker broker;
    @Mock
    private FutureCallback<Empty> listener;
    @Mock
    private DOMDataTreeReadTransaction readOnlyTx;
    @Mock
    private AbstractWriteTx writeOnlyTx1;
    @Mock
    private AbstractWriteTx writeOnlyTx2;
    @Mock
    private AbstractWriteTx writeOnlyTx3;
    @Mock
    private Registration registration1;
    @Mock
    private Registration registration2;
    @Mock
    private Registration registration3;
    @Captor
    private ArgumentCaptor<TxListener> captor;
    private TxChain chain;

    @BeforeEach
    void setUp() {
        chain = new TxChain(broker);
        chain.addCallback(listener);
    }

    @Test
    void testNewReadOnlyTransactionPrevSubmitted() {
        when(broker.newReadOnlyTransaction()).thenReturn(readOnlyTx);
        when(broker.newWriteOnlyTransaction()).thenReturn(writeOnlyTx1)
            .thenReturn(writeOnlyTx2).thenReturn(writeOnlyTx3);
        when(writeOnlyTx1.addListener(any())).thenReturn(registration1);
        chain.newWriteOnlyTransaction();
        verify(writeOnlyTx1).addListener(captor.capture());
        captor.getValue().onTransactionSubmitted(writeOnlyTx1);
        chain.newReadOnlyTransaction();
    }

    @Test
    void testNewReadOnlyTransactionPrevNotSubmitted() {
        mockRegistration();
        chain.newWriteOnlyTransaction();
        assertThrows(IllegalStateException.class, chain::newReadOnlyTransaction);
    }

    @Test
    void testNewReadWriteTransactionPrevSubmitted() {
        when(broker.newReadOnlyTransaction()).thenReturn(readOnlyTx);
        mockRegistration();
        when(writeOnlyTx2.addListener(any())).thenReturn(registration2);
        chain.newReadWriteTransaction();
        verify(writeOnlyTx1).addListener(captor.capture());
        captor.getValue().onTransactionSubmitted(writeOnlyTx1);
        chain.newReadWriteTransaction();
    }

    @Test
    void testNewReadWriteTransactionPrevNotSubmitted() {
        when(broker.newReadOnlyTransaction()).thenReturn(readOnlyTx);
        mockRegistration();
        chain.newReadWriteTransaction();
        assertThrows(IllegalStateException.class, chain::newReadWriteTransaction);
    }

    @Test
    void testNewWriteOnlyTransactionPrevSubmitted() {
        mockRegistration();
        when(writeOnlyTx2.addListener(any())).thenReturn(registration2);
        chain.newWriteOnlyTransaction();
        verify(writeOnlyTx1).addListener(captor.capture());
        captor.getValue().onTransactionSubmitted(writeOnlyTx1);
        chain.newWriteOnlyTransaction();
    }

    @Test
    void testNewWriteOnlyTransactionPrevNotSubmitted() {
        mockRegistration();
        chain.newWriteOnlyTransaction();
        assertThrows(IllegalStateException.class, chain::newWriteOnlyTransaction);
    }

    @Test
    void testCloseAfterFinished() {
        chain.close();
        verify(listener).onSuccess(Empty.value());
        assertThrows(DOMTransactionChainClosedException.class, chain::newReadOnlyTransaction);
    }

    @Test
    void testChainFail() {
        mockRegistration();
        final AbstractWriteTx writeTx = chain.newWriteOnlyTransaction();
        verify(writeOnlyTx1).addListener(captor.capture());
        writeTx.commit();
        final TransactionCommitFailedException cause = new TransactionCommitFailedException("fail");
        captor.getValue().onTransactionFailed(writeOnlyTx1, cause);
        verify(registration1).close();
        verify(listener).onFailure(cause);
    }

    @Test
    void testChainSuccess() {
        mockRegistration();
        final AbstractWriteTx writeTx = chain.newWriteOnlyTransaction();
        chain.close();
        verify(writeOnlyTx1).addListener(captor.capture());
        writeTx.commit();
        captor.getValue().onTransactionSuccessful(writeOnlyTx1);
        verify(registration1).close();
        verify(listener).onSuccess(Empty.value());
    }

    @Test
    void testCancel() {
        when(broker.newWriteOnlyTransaction()).thenReturn(writeOnlyTx1)
            .thenReturn(writeOnlyTx2).thenReturn(writeOnlyTx3);
        when(writeOnlyTx1.addListener(any())).thenReturn(registration1);
        when(writeOnlyTx2.addListener(any())).thenReturn(registration2);
        final AbstractWriteTx writeTx = chain.newWriteOnlyTransaction();
        verify(writeOnlyTx1).addListener(captor.capture());
        writeTx.cancel();
        captor.getValue().onTransactionCancelled(writeOnlyTx1);
        chain.newWriteOnlyTransaction();
    }

    @Test
    void testMultiplePendingTransactions() {
        mockAllRegistrations();
        //create 1st tx
        final AbstractWriteTx writeTx1 = chain.newWriteOnlyTransaction();
        final var captor1 = ArgumentCaptor.forClass(TxListener.class);
        verify(writeOnlyTx1).addListener(captor1.capture());
        //submit 1st tx
        writeTx1.commit();
        captor1.getValue().onTransactionSubmitted(writeOnlyTx1);

        //create 2nd tx
        final AbstractWriteTx writeTx2 = chain.newWriteOnlyTransaction();
        final var captor2 = ArgumentCaptor.forClass(TxListener.class);
        verify(writeTx2).addListener(captor2.capture());
        //submit 2nd tx
        writeTx2.commit();
        captor2.getValue().onTransactionSubmitted(writeOnlyTx2);

        //create 3rd tx
        final AbstractWriteTx writeTx3 = chain.newWriteOnlyTransaction();
        final var captor3 = ArgumentCaptor.forClass(TxListener.class);
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
        verify(listener).onSuccess(Empty.value());
    }

    @Test
    void testMultiplePendingTransactionsFail() {
        mockAllRegistrations();
        //create 1st tx
        final AbstractWriteTx writeTx1 = chain.newWriteOnlyTransaction();
        final var captor1 = ArgumentCaptor.forClass(TxListener.class);
        verify(writeOnlyTx1).addListener(captor1.capture());
        //submit 1st tx
        writeTx1.commit();
        captor1.getValue().onTransactionSubmitted(writeOnlyTx1);

        //create 2nd tx
        final AbstractWriteTx writeTx2 = chain.newWriteOnlyTransaction();
        final var captor2 = ArgumentCaptor.forClass(TxListener.class);
        verify(writeTx2).addListener(captor2.capture());
        //submit 2nd tx
        writeTx2.commit();
        captor2.getValue().onTransactionSubmitted(writeOnlyTx2);

        //create 3rd tx
        final AbstractWriteTx writeTx3 = chain.newWriteOnlyTransaction();
        final var captor3 = ArgumentCaptor.forClass(TxListener.class);
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
        verify(listener).onFailure(cause1);
        // 1 transaction failed, onTransactionChainSuccessful must not be called
        verify(listener, never()).onSuccess(any());
    }

    private void mockRegistration() {
        when(broker.newWriteOnlyTransaction()).thenReturn(writeOnlyTx1).thenReturn(writeOnlyTx2)
            .thenReturn(writeOnlyTx3);
        when(writeOnlyTx1.addListener(any())).thenReturn(registration1);
    }

    private void mockAllRegistrations() {
        mockRegistration();
        when(writeOnlyTx2.addListener(any())).thenReturn(registration2);
        when(writeOnlyTx3.addListener(any())).thenReturn(registration3);
    }
}
