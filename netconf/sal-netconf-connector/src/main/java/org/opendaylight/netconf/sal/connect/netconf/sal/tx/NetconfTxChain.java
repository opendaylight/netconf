/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.base.Preconditions;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.netconf.api.tx.NetconfDOMDataBrokerOperations;
import org.opendaylight.netconf.api.tx.NetconfOperationDOMTransactionChain;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceDataBroker;

public class NetconfTxChain extends TxChain implements NetconfOperationDOMTransactionChain {
    private final NetconfDOMDataBrokerOperations operationDataBroker;

    public NetconfTxChain(final NetconfDeviceDataBroker dataBroker,
                          final NetconfDOMDataBrokerOperations operationDataBroker,
                          final DOMTransactionChainListener listener) {
        super(dataBroker, listener);
        this.operationDataBroker = operationDataBroker;
    }

    @Override
    public synchronized DOMDataTreeWriteTransaction newCreateOperationWriteTransaction() {
        checkOperationPermitted();
        final DOMDataTreeWriteTransaction writeTransaction = operationDataBroker.newCreateOperationWriteTransaction();
        Preconditions.checkState(writeTransaction instanceof AbstractWriteTx);
        final AbstractWriteTx pendingWriteTx = (AbstractWriteTx) writeTransaction;
        pendingTransactions.put(pendingWriteTx, pendingWriteTx.addListener(this));
        currentTransaction = pendingWriteTx;
        return pendingWriteTx;
    }

    @Override
    public DOMDataTreeReadWriteTransaction newCreateOperationReadWriteTransaction() {
        return new ReadWriteTx(operationDataBroker.newReadOnlyTransaction(), newCreateOperationWriteTransaction());
    }
}
