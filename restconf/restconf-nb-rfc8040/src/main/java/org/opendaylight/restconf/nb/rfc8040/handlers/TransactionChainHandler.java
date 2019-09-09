/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
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
@Singleton
public class TransactionChainHandler implements Handler<DOMTransactionChain>, AutoCloseable {
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

    private final DOMDataBroker dataBroker;
    private final Queue<DOMTransactionChain> transactionChainList;

    /**
     * Prepare transaction chain service for Restconf services.
     */
    @Inject
    public TransactionChainHandler(final DOMDataBroker dataBroker) {
        this.dataBroker = Objects.requireNonNull(dataBroker);
        this.transactionChainList = new ConcurrentLinkedQueue<>();
    }

    /**
     * Create and return new instance of object {@link DOMTransactionChain}.
     * After use, is important to close transactionChain by method {@link DOMTransactionChain#close()}.
     * @return new instance of object {@link DOMTransactionChain}
     */
    @Override
    public DOMTransactionChain get() {
        final DOMTransactionChain transactionChain = dataBroker.createTransactionChain(transactionChainListener);
        this.transactionChainList.add(transactionChain);
        LOG.trace("Started TransactionChain({})", transactionChain);
        return transactionChain;
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        for (DOMTransactionChain transactionChain: this.transactionChainList) {
            transactionChain.close();
            LOG.trace("Closed TransactionChain({})", transactionChain);
        }
    }

    /**
     * Verify if {@link DOMTransactionChain} exist in {@link TransactionChainHandler} queue.
     * @param transactionChain object to check.
     * @return true if object still exist in {@link TransactionChainHandler}.
     */
    boolean verifyIfExistTransactionChain(DOMTransactionChain transactionChain) {
        return this.transactionChainList.contains(transactionChain);
    }
}
