/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;

/**
 * {@link DOMTransactionChain} implementation for Netconf connector.
 */
final class TxChain extends AbstractTxChain {
    TxChain(final DOMDataBroker dataBroker, final DOMTransactionChainListener listener) {
        super(dataBroker, listener);
    }

    @Override
    public synchronized DOMDataTreeReadTransaction newReadOnlyTransaction() {
        checkOperationPermitted();
        return dataBroker.newReadOnlyTransaction();
    }

    @Override
    public synchronized DOMDataTreeReadWriteTransaction newReadWriteTransaction() {
        return new ReadWriteTx<>(dataBroker.newReadOnlyTransaction(), newWriteOnlyTransaction());
    }
}
