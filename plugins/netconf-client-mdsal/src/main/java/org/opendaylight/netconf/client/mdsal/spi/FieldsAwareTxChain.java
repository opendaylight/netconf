/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static java.util.Objects.requireNonNull;

import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMDataBrokerFieldsExtension;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadTransaction;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadWriteTransaction;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsTransactionChain;

public final class FieldsAwareTxChain extends AbstractTxChain implements NetconfDOMFieldsTransactionChain {
    private final NetconfDOMDataBrokerFieldsExtension dataBrokerFieldsExtension;

    FieldsAwareTxChain(final DOMDataBroker dataBroker, final DOMTransactionChainListener listener,
                              final NetconfDOMDataBrokerFieldsExtension dataBrokerFieldsExtension) {
        super(dataBroker, listener);
        this.dataBrokerFieldsExtension = requireNonNull(dataBrokerFieldsExtension);
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