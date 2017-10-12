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
import com.google.common.util.concurrent.CheckedFuture;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionProvider implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionProvider.class);

    private final DOMDataBroker dataBroker;

    private DOMDataReadWriteTransaction candidateTransaction = null;
    private DOMDataReadWriteTransaction runningTransaction = null;
    private final List<DOMDataReadWriteTransaction> allOpenReadWriteTransactions = new ArrayList<>();

    private final String netconfSessionIdForReporting;

    private static final String NO_TRANSACTION_FOUND_FOR_SESSION = "No candidateTransaction found for session ";

    public TransactionProvider(final DOMDataBroker dataBroker, final String netconfSessionIdForReporting) {
        this.dataBroker = dataBroker;
        this.netconfSessionIdForReporting = netconfSessionIdForReporting;
    }

    @Override
    public synchronized void close() throws Exception {
        for (final DOMDataReadWriteTransaction rwt : allOpenReadWriteTransactions) {
            rwt.cancel();
        }

        allOpenReadWriteTransactions.clear();
    }

    public synchronized Optional<DOMDataReadWriteTransaction> getCandidateTransaction() {
        if (candidateTransaction == null) {
            return Optional.absent();
        }

        return Optional.of(candidateTransaction);
    }

    public synchronized DOMDataReadWriteTransaction getOrCreateTransaction() {
        if (getCandidateTransaction().isPresent()) {
            return getCandidateTransaction().get();
        }

        candidateTransaction = dataBroker.newReadWriteTransaction();
        allOpenReadWriteTransactions.add(candidateTransaction);
        return candidateTransaction;
    }

    public synchronized boolean commitTransaction() throws DocumentedException {
        if (!getCandidateTransaction().isPresent()) {
            //making empty commit without prior opened transaction, just return true
            LOG.debug("Making commit without open candidate transaction for session {}", netconfSessionIdForReporting);
            return true;
        }

        final CheckedFuture<Void, TransactionCommitFailedException> future = candidateTransaction.submit();
        try {
            future.checkedGet();
        } catch (final TransactionCommitFailedException e) {
            LOG.debug("Transaction {} failed on", candidateTransaction, e);
            final String cause = e.getCause() != null ? (" Cause: " + e.getCause().getMessage()) : "";
            throw new DocumentedException(
                    "Transaction commit failed on " + e.getMessage() + " " + netconfSessionIdForReporting + cause, e,
                    ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        } finally {
            allOpenReadWriteTransactions.remove(candidateTransaction);
            candidateTransaction = null;
        }

        return true;
    }

    public synchronized void abortTransaction() {
        LOG.debug("Aborting current candidateTransaction");
        final Optional<DOMDataReadWriteTransaction> otx = getCandidateTransaction();
        if (!otx.isPresent()) {
            LOG.warn("discard-changes triggerd on an empty transaction for session: {}", netconfSessionIdForReporting);
            return;
        }
        candidateTransaction.cancel();
        allOpenReadWriteTransactions.remove(candidateTransaction);
        candidateTransaction = null;
    }

    public synchronized DOMDataReadWriteTransaction createRunningTransaction() {
        runningTransaction = dataBroker.newReadWriteTransaction();
        allOpenReadWriteTransactions.add(runningTransaction);
        return runningTransaction;
    }

    public synchronized void abortRunningTransaction(final DOMDataReadWriteTransaction tx) {
        LOG.debug("Aborting current running Transaction");
        Preconditions.checkState(runningTransaction != null,
                NO_TRANSACTION_FOUND_FOR_SESSION + netconfSessionIdForReporting);
        tx.cancel();
        allOpenReadWriteTransactions.remove(tx);
    }

}
