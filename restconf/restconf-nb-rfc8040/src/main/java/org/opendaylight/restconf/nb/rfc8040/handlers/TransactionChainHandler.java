/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import java.util.Objects;
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
            reset();
            throw new RestconfDocumentedException("TransactionChain(" + chain + ") not committed correctly", cause);
        }

        @Override
        public void onTransactionChainSuccessful(final DOMTransactionChain chain) {
            LOG.trace("TransactionChain({}) SUCCESSFUL", chain);
        }
    };

    private final DOMDataBroker dataBroker;

    private volatile DOMTransactionChain transactionChain;

    /**
     * Prepare transaction chain service for Restconf services.
     */
    @Inject
    public TransactionChainHandler(final DOMDataBroker dataBroker) {
        this.dataBroker = Objects.requireNonNull(dataBroker);
        transactionChain = Objects.requireNonNull(dataBroker.createTransactionChain(transactionChainListener));
    }

    @Override
    public DOMTransactionChain get() {
        return this.transactionChain;
    }

    public synchronized void reset() {
        LOG.trace("Resetting TransactionChain({})", transactionChain);
        transactionChain.close();
        transactionChain = dataBroker.createTransactionChain(transactionChainListener);
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        transactionChain.close();
    }
}
