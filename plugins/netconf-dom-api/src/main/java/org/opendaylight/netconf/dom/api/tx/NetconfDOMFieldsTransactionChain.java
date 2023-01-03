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
     * {@inheritDoc}
     *
     * <p>
     * Returned transaction supports specification of returned fields.
     */
    @Override
    NetconfDOMFieldsReadTransaction newReadOnlyTransaction();

    /**
     * {@inheritDoc}
     *
     * <p>
     * Returned transaction supports specification of returned fields.
     */
    @Override
    NetconfDOMFieldsReadWriteTransaction newReadWriteTransaction();
}
