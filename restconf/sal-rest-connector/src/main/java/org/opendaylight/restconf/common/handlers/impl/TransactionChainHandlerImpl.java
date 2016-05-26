/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.handlers.impl;

import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.handlers.api.TransactionChainHandler;


/**
 * Implementation of {@link TransactionChainHandler}
 */
public class TransactionChainHandlerImpl implements TransactionChainHandler {

    private DOMTransactionChain transactionChain;

    @Override
    public DOMTransactionChain getTransactionChain() {
        return this.transactionChain;
    }

    @Override
    public void setTransactionChain(final DOMTransactionChain transactionChain) {
        this.transactionChain = transactionChain;
    }

}
