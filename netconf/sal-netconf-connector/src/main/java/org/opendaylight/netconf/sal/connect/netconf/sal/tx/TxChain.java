/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainClosedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DOMTransactionChain} implementation for Netconf connector.
 */
public class TxChain implements DOMTransactionChain, TxListener {

    private static final Logger LOG = LoggerFactory.getLogger(TxChain.class);

    private final DOMDataBroker dataBroker;
    private final TransactionChainListener listener;
    /**
     * Submitted transactions, which hasn't completed yet.
     */
    private final Map<DOMDataWriteTransaction, AutoCloseable> pendingTransactions = new HashMap<>();

    /**
     * Transaction created by this chain, which was not submitted or cancelled.
     */
    private AbstractWriteTx currentTransaction = null;
    private boolean closed = false;
    private boolean successful = true;

    public TxChain(final DOMDataBroker dataBroker, final TransactionChainListener listener) {
        this.dataBroker = dataBroker;
        this.listener = listener;
    }

    @Override
    public synchronized DOMDataReadOnlyTransaction newReadOnlyTransaction() {
        checkOperationPermitted();
        return dataBroker.newReadOnlyTransaction();
    }

    @Override
    public synchronized AbstractWriteTx newWriteOnlyTransaction() {
        checkOperationPermitted();
        final DOMDataWriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        Preconditions.checkState(writeTransaction instanceof AbstractWriteTx);
        final AbstractWriteTx pendingWriteTx = (AbstractWriteTx) writeTransaction;
        pendingTransactions.put(pendingWriteTx, pendingWriteTx.addListener(this));
        currentTransaction = pendingWriteTx;
        return pendingWriteTx;
    }

    @Override
    public synchronized DOMDataReadWriteTransaction newReadWriteTransaction() {
        return new ReadWriteTx(dataBroker.newReadOnlyTransaction(), newWriteOnlyTransaction());
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            notifyChainListenerSuccess();
        }
    }

    @Override
    public synchronized void onTransactionSuccessful(final AbstractWriteTx transaction) {
        removePendingTx(transaction);
        notifyChainListenerSuccess();
    }

    @Override
    public synchronized void onTransactionFailed(final AbstractWriteTx transaction, final Throwable cause) {
        removePendingTx(transaction);
        successful = false;
        if (currentTransaction != null) {
            currentTransaction.cancel();
        }
        listener.onTransactionChainFailed(this, transaction, cause);
    }

    @Override
    public synchronized void onTransactionSubmitted(final AbstractWriteTx transaction) {
        currentTransaction = null;
    }

    @Override
    public synchronized void onTransactionCancelled(final AbstractWriteTx transaction) {
        removePendingTx(transaction);
        currentTransaction = null;
    }

    private void removePendingTx(final AbstractWriteTx transaction) {
        try {
            pendingTransactions.remove(transaction).close();
        } catch (final Exception e) {
            LOG.error("Can't remove transaction listener registration", e);
        }
    }

    /**
     * Checks, if chain isn't closed and if there is no not submitted write transaction waiting.
     */
    private void checkOperationPermitted() {
        if (closed) {
            throw new TransactionChainClosedException("Transaction chain was closed");
        }
        Preconditions.checkState(currentTransaction == null, "Last write transaction has not finished yet");
    }

    private void notifyChainListenerSuccess() {
        if (closed && pendingTransactions.isEmpty() && successful) {
            listener.onTransactionChainSuccessful(this);
        }
    }

}
