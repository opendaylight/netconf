/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dom.api.tx;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;

/**
 * DOM data broker extension with an option to read only selected fields under parent data node.
 */
public interface NetconfDOMDataBrokerFieldsExtension extends DOMDataBrokerExtension {

    /**
     * Creates a new read-only transaction with an option to read only selected fields from target data node.
     *
     * @return A new read-only transaction.
     */
    @NonNull
    NetconfDOMFieldsReadTransaction newReadOnlyTransaction();

    /**
     * Creates a new read-write transaction with an option to read only selected fields from target data node.
     *
     * @return A new read-write transaction.
     */
    @NonNull
    NetconfDOMFieldsReadWriteTransaction newReadWriteTransaction();

    /**
     * Creates a new transaction chain with an option to read only selected fields from target data node.
     *
     * @param listener Transaction chain event listener.
     * @return A new transaction chain.
     */
    @NonNull
    NetconfDOMFieldsTransactionChain createTransactionChain(DOMTransactionChainListener listener);
}