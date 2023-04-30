/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal;

import com.google.common.util.concurrent.FluentFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TransactionProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionProvider.class);

    private final List<DOMDataTreeReadWriteTransaction> allOpenReadWriteTransactions = new ArrayList<>();
    private final DOMDataTransactionValidator transactionValidator;
    private final DOMDataBroker dataBroker;
    private final SessionIdType sessionId;

    private DOMDataTreeReadWriteTransaction candidateTransaction = null;
    private DOMDataTreeReadWriteTransaction runningTransaction = null;

    public TransactionProvider(final DOMDataBroker dataBroker, final SessionIdType sessionId) {
        this.dataBroker = dataBroker;
        this.sessionId = sessionId;
        transactionValidator = dataBroker.getExtensions().getInstance(DOMDataTransactionValidator.class);
    }

    @Override
    public synchronized void close() {
        for (var rwt : allOpenReadWriteTransactions) {
            rwt.cancel();
        }

        allOpenReadWriteTransactions.clear();
    }

    public synchronized Optional<DOMDataTreeReadWriteTransaction> getCandidateTransaction() {
        return Optional.ofNullable(candidateTransaction);
    }

    public synchronized DOMDataTreeReadWriteTransaction getOrCreateTransaction() {
        if (candidateTransaction == null) {
            candidateTransaction = dataBroker.newReadWriteTransaction();
            allOpenReadWriteTransactions.add(candidateTransaction);
        }
        return candidateTransaction;
    }

    public synchronized void validateTransaction() throws DocumentedException {
        if (transactionValidator == null) {
            LOG.error("Validate capability is not supported");
            throw new DocumentedException("Validate capability is not supported",
                ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED, ErrorSeverity.ERROR);
        }

        if (candidateTransaction == null) {
            // Validating empty transaction, just return true
            LOG.debug("Validating empty candidate transaction for session {}", sessionId.getValue());
            return;
        }

        try {
            transactionValidator.validate(candidateTransaction).get();
        } catch (final InterruptedException | ExecutionException e) {
            LOG.debug("Candidate transaction validation {} failed on session {}", candidateTransaction,
                sessionId.getValue(), e);
            final String cause = e.getCause() != null ? " Cause: " + e.getCause().getMessage() : "";
            throw new DocumentedException("Candidate transaction validate failed [sessionId="
                    + sessionId.getValue() + "]: " + e.getMessage() + cause, e, ErrorType.APPLICATION,
                    ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }
    }

    public synchronized boolean commitTransaction() throws DocumentedException {
        if (candidateTransaction == null) {
            //making empty commit without prior opened transaction, just return true
            LOG.debug("Making commit without open candidate transaction for session {}", sessionId.getValue());
            return true;
        }

        final FluentFuture<? extends CommitInfo> future = candidateTransaction.commit();
        try {
            future.get();
        } catch (final InterruptedException | ExecutionException e) {
            LOG.debug("Transaction {} failed on", candidateTransaction, e);
            final String cause = e.getCause() != null ? " Cause: " + e.getCause().getMessage() : "";
            throw new DocumentedException("Transaction commit failed on " + e.getMessage() + " "
                    + sessionId.getValue() + cause, e, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
                    ErrorSeverity.ERROR);
        } finally {
            allOpenReadWriteTransactions.remove(candidateTransaction);
            candidateTransaction = null;
        }

        return true;
    }

    public synchronized void abortTransaction() {
        LOG.debug("Aborting current candidateTransaction");
        if (candidateTransaction == null) {
            LOG.warn("discard-changes triggerd on an empty transaction for session: {}", sessionId.getValue());
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
        if (runningTransaction == null) {
            throw new IllegalStateException("No candidateTransaction found for session " + sessionId.getValue());
        }
        tx.cancel();
        allOpenReadWriteTransactions.remove(tx);
    }
}
