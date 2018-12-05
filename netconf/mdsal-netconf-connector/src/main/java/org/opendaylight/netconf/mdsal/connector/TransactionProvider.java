/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FluentFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.DocumentedException.ErrorSeverity;
import org.opendaylight.netconf.api.DocumentedException.ErrorTag;
import org.opendaylight.netconf.api.DocumentedException.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionProvider.class);

    private final DOMDataBroker dataBroker;

    private DOMDataTreeReadWriteTransaction candidateTransaction = null;
    private DOMDataTreeReadWriteTransaction runningTransaction = null;
    private final List<DOMDataTreeReadWriteTransaction> allOpenReadWriteTransactions = new ArrayList<>();
    private final DOMDataTransactionValidator transactionValidator;

    private final String netconfSessionIdForReporting;

    private static final String NO_TRANSACTION_FOUND_FOR_SESSION = "No candidateTransaction found for session ";

    public TransactionProvider(final DOMDataBroker dataBroker, final String netconfSessionIdForReporting) {
        this.dataBroker = dataBroker;
        this.netconfSessionIdForReporting = netconfSessionIdForReporting;
        this.transactionValidator = dataBroker.getExtensions().getInstance(DOMDataTransactionValidator.class);
    }

    @Override
    public synchronized void close() {
        for (final DOMDataTreeReadWriteTransaction rwt : allOpenReadWriteTransactions) {
            rwt.cancel();
        }

        allOpenReadWriteTransactions.clear();
    }

    public synchronized Optional<DOMDataTreeReadWriteTransaction> getCandidateTransaction() {
        if (candidateTransaction == null) {
            return Optional.absent();
        }

        return Optional.of(candidateTransaction);
    }

    public synchronized DOMDataTreeReadWriteTransaction getOrCreateTransaction() {
        if (getCandidateTransaction().isPresent()) {
            return getCandidateTransaction().get();
        }

        candidateTransaction = dataBroker.newReadWriteTransaction();
        allOpenReadWriteTransactions.add(candidateTransaction);
        return candidateTransaction;
    }

    public synchronized void validateTransaction() throws DocumentedException {
        if (transactionValidator == null) {
            LOG.error("Validate capability is not supported");
            throw new DocumentedException("Validate capability is not supported",
                ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED, ErrorSeverity.ERROR);
        }

        if (!getCandidateTransaction().isPresent()) {
            // Validating empty transaction, just return true
            LOG.debug("Validating empty candidate transaction for session {}", netconfSessionIdForReporting);
            return;
        }

        try {
            transactionValidator.validate(candidateTransaction).get();
        } catch (final InterruptedException | ExecutionException e) {
            LOG.debug("Candidate transaction validation {} failed on session {}", candidateTransaction,
                netconfSessionIdForReporting, e);
            final String cause = e.getCause() != null ? " Cause: " + e.getCause().getMessage() : "";
            throw new DocumentedException("Candidate transaction validate failed [sessionId="
                    + netconfSessionIdForReporting + "]: " + e.getMessage() + cause, e, ErrorType.APPLICATION,
                    ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }
    }

    public synchronized boolean commitTransaction() throws DocumentedException {
        if (!getCandidateTransaction().isPresent()) {
            //making empty commit without prior opened transaction, just return true
            LOG.debug("Making commit without open candidate transaction for session {}", netconfSessionIdForReporting);
            return true;
        }

        final FluentFuture<? extends CommitInfo> future = candidateTransaction.commit();
        try {
            future.get();
        } catch (final InterruptedException | ExecutionException e) {
            LOG.debug("Transaction {} failed on", candidateTransaction, e);
            final String cause = e.getCause() != null ? " Cause: " + e.getCause().getMessage() : "";
            throw new DocumentedException("Transaction commit failed on " + e.getMessage() + " "
                    + netconfSessionIdForReporting + cause, e, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
                    ErrorSeverity.ERROR);
        } finally {
            allOpenReadWriteTransactions.remove(candidateTransaction);
            candidateTransaction = null;
        }

        return true;
    }

    public synchronized void abortTransaction() {
        LOG.debug("Aborting current candidateTransaction");
        final Optional<DOMDataTreeReadWriteTransaction> otx = getCandidateTransaction();
        if (!otx.isPresent()) {
            LOG.warn("discard-changes triggerd on an empty transaction for session: {}", netconfSessionIdForReporting);
            return;
        }
        candidateTransaction.cancel();
        allOpenReadWriteTransactions.remove(candidateTransaction);
        candidateTransaction = null;
    }

    public synchronized DOMDataTreeReadWriteTransaction createRunningTransaction() {
        runningTransaction = dataBroker.newReadWriteTransaction();
        allOpenReadWriteTransactions.add(runningTransaction);
        return runningTransaction;
    }

    public synchronized void abortRunningTransaction(final DOMDataTreeReadWriteTransaction tx) {
        LOG.debug("Aborting current running Transaction");
        Preconditions.checkState(runningTransaction != null,
                NO_TRANSACTION_FOUND_FOR_SESSION + netconfSessionIdForReporting);
        tx.cancel();
        allOpenReadWriteTransactions.remove(tx);
    }
}
