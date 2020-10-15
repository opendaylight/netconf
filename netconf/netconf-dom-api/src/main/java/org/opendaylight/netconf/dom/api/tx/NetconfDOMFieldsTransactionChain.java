/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dom.api.tx;

import org.opendaylight.mdsal.dom.api.DOMTransactionChain;

/**
 * DOM transaction chain that is extended by reading of specific fields fom parent node using dedicated methods
 * in created read-only or read-write transactions.
 */
public interface NetconfDOMFieldsTransactionChain extends DOMTransactionChain {

    /**
     * Creates a new read-only transaction with an option to read only selected fields from target data node.
     *
     * @return A new read-only transaction.
     */
    @Override
    NetconfDOMFieldsReadTransaction newReadOnlyTransaction();

    /**
     * Creates a new read-write transaction with an option to read only selected fields from target data node.
     *
     * @return A new read-write transaction.
     */
    @Override
    NetconfDOMFieldsReadWriteTransaction newReadWriteTransaction();
}