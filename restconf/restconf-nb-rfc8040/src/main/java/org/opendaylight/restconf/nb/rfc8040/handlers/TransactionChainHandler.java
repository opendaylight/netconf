/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import static java.util.Objects.requireNonNull;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link TransactionChainHandler}.
 */
// FIXME: untangle this class, what good is it, really?!
public class TransactionChainHandler implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionChainHandler.class);

    private final DOMTransactionChainListener transactionChainListener = new DOMTransactionChainListener() {
        @Override
        public void onTransactionChainFailed(final DOMTransactionChain chain, final DOMDataTreeTransaction transaction,
                final Throwable cause) {
            LOG.warn("TransactionChain({}) {} FAILED!", chain, transaction.getIdentifier(), cause);
            transactionChainList.remove(chain);
            throw new RestconfDocumentedException("TransactionChain(" + chain + ") not committed correctly", cause);
        }

        @Override
        public void onTransactionChainSuccessful(final DOMTransactionChain chain) {
            LOG.trace("TransactionChain({}) SUCCESSFUL", chain);
            transactionChainList.remove(chain);
        }
    };

    private final Queue<DOMTransactionChain> transactionChainList = new ConcurrentLinkedQueue<>();
    private final DOMDataBroker dataBroker;

    /**
     * Prepare transaction chain service for Restconf services.
     */
    public TransactionChainHandler(final DOMDataBroker dataBroker) {
        this.dataBroker = requireNonNull(dataBroker);
    }

    /**
     * Create and return new instance of object {@link DOMTransactionChain}.
     * After use, is important to close transactionChain by method {@link DOMTransactionChain#close()}.
     * @return new instance of object {@link DOMTransactionChain}
     */
    public DOMTransactionChain get() {
        final DOMTransactionChain transactionChain = dataBroker.createTransactionChain(transactionChainListener);
        this.transactionChainList.add(transactionChain);
        LOG.trace("Started TransactionChain({})", transactionChain);
        return transactionChain;
    }

    @Override
    public synchronized void close() {
        for (DOMTransactionChain transactionChain : this.transactionChainList) {
            transactionChain.close();
            LOG.trace("Closed TransactionChain({})", transactionChain);
        }
    }

    /**
     * Verify if {@link DOMTransactionChain} exist in {@link TransactionChainHandler} queue.
     * @param transactionChain object to check.
     * @return true if object still exist in {@link TransactionChainHandler}.
     */
    boolean verifyIfExistTransactionChain(final DOMTransactionChain transactionChain) {
        return this.transactionChainList.contains(transactionChain);
    }
}
