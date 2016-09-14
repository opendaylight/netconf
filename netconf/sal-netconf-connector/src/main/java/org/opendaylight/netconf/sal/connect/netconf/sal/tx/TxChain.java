/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.base.Preconditions;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private AutoCloseable txListenerRegistration;
    /**
     * Last write transaction success status. Transaction is successful, if it completes successfully
     * or is cancelled.
     */
    private final AtomicBoolean lastTxSuccessful = new AtomicBoolean(true);
    /**
     * Last write transaction status. Transaction is finished, if it completes successfully,
     * is cancelled or fail.
     */
    private final AtomicBoolean lastTxFinished = new AtomicBoolean(true);
    /**
     * This TxChain status
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);


    public TxChain(final DOMDataBroker dataBroker, final TransactionChainListener listener) {
        this.dataBroker = dataBroker;
        this.listener = listener;
    }

    @Override
    public DOMDataReadOnlyTransaction newReadOnlyTransaction() {
        checkOperationPermitted();
        return dataBroker.newReadOnlyTransaction();
    }

    @Override
    public DOMDataReadWriteTransaction newReadWriteTransaction() {
        return new ReadWriteTx(dataBroker.newReadOnlyTransaction(), newWriteOnlyTransaction());
    }

    @Override
    public AbstractWriteTx newWriteOnlyTransaction() {
        tryToLockChain();
        final DOMDataWriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        Preconditions.checkState(writeTransaction instanceof AbstractWriteTx);
        final AbstractWriteTx pendingWriteTx = (AbstractWriteTx) writeTransaction;
        txListenerRegistration = pendingWriteTx.addListener(this);
        return pendingWriteTx;
    }

    /**
     * Checks, if chain isn't closed and if there is no not completed write transaction waiting.
     */
    private void checkOperationPermitted() {
        if (closed.get()) {
            throw new TransactionChainClosedException("Transaction chain was closed");
        }
        Preconditions.checkState(lastTxFinished.get(), "Last write transaction has not finished yet");
    }

    /**
     * Disables creation of new transactions.
     */
    private void tryToLockChain() {
        if (closed.get()) {
            throw new TransactionChainClosedException("Transaction chain was closed");
        }
        Preconditions.checkState(lastTxFinished.compareAndSet(true, false), "Last write transaction has not finished yet");
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            // notify listener only about success, failure notification is handled in onTransactionFailed()
            // if last transaction isn't finished yet, notifyChainListenerSuccess() will notify about success later
            if (lastTxFinished.get() && lastTxSuccessful.get()) {
                listener.onTransactionChainSuccessful(this);
            }
        }
    }

    @Override
    public void onTransactionSuccessful(final AbstractWriteTx transaction) {
        notifyChainListenerSuccess();
    }

    @Override
    public void onTransactionFailed(final AbstractWriteTx transaction, final Throwable cause) {
        transaction.cancel();
        setLastFinishedTxStatus(false);
        listener.onTransactionChainFailed(this, transaction, cause);
    }

    @Override
    public void onTransactionCancelled(final AbstractWriteTx transaction) {
        notifyChainListenerSuccess();
    }

    private void setLastFinishedTxStatus(final boolean txStatus) {
        lastTxFinished.set(true);
        lastTxSuccessful.set(txStatus);
        try {
            if (txListenerRegistration != null) {
                txListenerRegistration.close();
            }
        } catch (final Exception e) {
            LOG.warn("Cannot remove listener registration", e);
        }
    }

    private void notifyChainListenerSuccess() {
        setLastFinishedTxStatus(true);
        if (closed.get()) {
            listener.onTransactionChainSuccessful(this);
        }
    }

}
