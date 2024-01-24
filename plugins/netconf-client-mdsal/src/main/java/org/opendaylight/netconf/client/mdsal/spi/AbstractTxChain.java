/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainClosedException;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * {@link DOMTransactionChain} implementation for Netconf connector.
 */
abstract class AbstractTxChain implements DOMTransactionChain, TxListener {
    // Submitted transactions that haven't completed yet.
    private final Map<DOMDataTreeWriteTransaction, Registration> pendingTransactions = new HashMap<>();
    private final @NonNull SettableFuture<Empty> future = SettableFuture.create();

    final DOMDataBroker dataBroker;

    /**
     * Transaction created by this chain that hasn't been submitted or cancelled yet.
     */
    private AbstractWriteTx currentTransaction = null;
    private boolean closed = false;
    private boolean successful = true;

    AbstractTxChain(final DOMDataBroker dataBroker) {
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Override
    public final ListenableFuture<Empty> future() {
        return future;
    }

    @Override
    public final synchronized AbstractWriteTx newWriteOnlyTransaction() {
        checkOperationPermitted();

        final var writeTransaction = dataBroker.newWriteOnlyTransaction();
        if (!(writeTransaction instanceof AbstractWriteTx pendingWriteTx)) {
            throw new IllegalStateException("Unexpected transaction " + writeTransaction);
        }
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
        future.setException(cause);
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

    private void removePendingTx(final AbstractWriteTx transaction) {
        pendingTransactions.remove(transaction).close();
    }

    private void notifyChainListenerSuccess() {
        if (closed && pendingTransactions.isEmpty() && successful) {
            future.set(Empty.value());
        }
    }
}
