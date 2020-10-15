/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx.fields;

import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMDataBrokerFieldsExtension;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadTransaction;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadWriteTransaction;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsTransactionChain;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.TxChain;

public final class FieldsAwareTxChain extends TxChain implements NetconfDOMFieldsTransactionChain {

    private final NetconfDOMDataBrokerFieldsExtension dataBrokerFieldsExtension;

    public FieldsAwareTxChain(final DOMDataBroker dataBroker, final DOMTransactionChainListener listener,
                              final NetconfDOMDataBrokerFieldsExtension dataBrokerFieldsExtension) {
        super(dataBroker, listener);
        this.dataBrokerFieldsExtension = dataBrokerFieldsExtension;
    }

    @Override
    public synchronized NetconfDOMFieldsReadTransaction newReadOnlyTransaction() {
        checkOperationPermitted();
        return dataBrokerFieldsExtension.newReadOnlyTransaction();
    }

    @Override
    public synchronized NetconfDOMFieldsReadWriteTransaction newReadWriteTransaction() {
        return dataBrokerFieldsExtension.newReadWriteTransaction();
    }
}