/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static com.google.common.base.Preconditions.checkState;

import java.util.HashMap;
import java.util.Map;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainClosedException;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DOMTransactionChain} implementation for Netconf connector.
 */
abstract class AbstractTxChain implements DOMTransactionChain, TxListener {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTxChain.class);

    /**
     * Submitted transactions that haven't completed yet.
     */
    private final Map<DOMDataTreeWriteTransaction, AutoCloseable> pendingTransactions = new HashMap<>();

    final DOMDataBroker dataBroker;
    final DOMTransactionChainListener listener;

    /**
     * Transaction created by this chain that hasn't been submitted or cancelled yet.
     */
    private AbstractWriteTx currentTransaction = null;
    private boolean closed = false;
    private boolean successful = true;

    AbstractTxChain(final DOMDataBroker dataBroker, final DOMTransactionChainListener listener) {
        this.dataBroker = dataBroker;
        this.listener = listener;
    }

    @Override
    public final synchronized AbstractWriteTx newWriteOnlyTransaction() {
        checkOperationPermitted();
        final DOMDataTreeWriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        checkState(writeTransaction instanceof AbstractWriteTx);
        final AbstractWriteTx pendingWriteTx = (AbstractWriteTx) writeTransaction;
        pendingTransactions.put(pendingWriteTx, pendingWriteTx.addListener(this));
        currentTransaction = pendingWriteTx;
        return pendingWriteTx;
    }

    @Override
    public final synchronized void close() {
        if (!closed) {
            closed = true;
            notifyChainListenerSuccess();
        }
    }

    @Override
    public final synchronized void onTransactionSuccessful(final AbstractWriteTx transaction) {
        removePendingTx(transaction);
        notifyChainListenerSuccess();
    }

    @Override
    public final synchronized void onTransactionFailed(final AbstractWriteTx transaction, final Throwable cause) {
        removePendingTx(transaction);
        successful = false;
        if (currentTransaction != null) {
            currentTransaction.cancel();
        }
        listener.onTransactionChainFailed(this, transaction, cause);
    }

    @Override
    public final synchronized void onTransactionSubmitted(final AbstractWriteTx transaction) {
        currentTransaction = null;
    }

    @Override
    public final synchronized void onTransactionCancelled(final AbstractWriteTx transaction) {
        removePendingTx(transaction);
        currentTransaction = null;
    }

    /**
     * Checks, if chain isn't closed and if there is no not submitted write transaction waiting.
     */
    final void checkOperationPermitted() {
        if (closed) {
            throw new DOMTransactionChainClosedException("Transaction chain was closed");
        }
        checkState(currentTransaction == null, "Last write transaction has not finished yet");
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removePendingTx(final AbstractWriteTx transaction) {
        try {
            pendingTransactions.remove(transaction).close();
        } catch (final Exception e) {
            LOG.error("Can't remove transaction listener registration", e);
        }
    }

    private void notifyChainListenerSuccess() {
        if (closed && pendingTransactions.isEmpty() && successful) {
            listener.onTransactionChainSuccessful(this);
        }
    }
}
