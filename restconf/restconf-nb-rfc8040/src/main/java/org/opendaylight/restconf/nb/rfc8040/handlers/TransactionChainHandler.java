/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.handlers;

import com.google.common.base.Preconditions;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;


/**
 * Implementation of {@link TransactionChainHandler}.
 *
 */
public class TransactionChainHandler implements Handler<DOMTransactionChain> {

    private DOMTransactionChain transactionChain;

    /**
     * Prepare transaction chain service for Restconf services.
     *
     * @param transactionChain Transaction chain
     */
    public TransactionChainHandler(final DOMTransactionChain transactionChain) {
        Preconditions.checkNotNull(transactionChain);
        this.transactionChain = transactionChain;
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public void update(final DOMTransactionChain transactionChain) {
        Preconditions.checkNotNull(transactionChain);
        this.transactionChain = transactionChain;
    }

    @Override
    public DOMTransactionChain get() {
        return this.transactionChain;
    }
}
