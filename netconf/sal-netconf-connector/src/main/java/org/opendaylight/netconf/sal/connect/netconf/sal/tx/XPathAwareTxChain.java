/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.netconf.api.tx.NetconfXPathDOMTransactionChain;
import org.opendaylight.netconf.api.tx.NetconfXPathDataBrokerExtension;
import org.opendaylight.netconf.api.tx.XPathAwareDOMReadTransaction;
import org.opendaylight.netconf.api.tx.XPathAwareDOMReadWriteTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.xpath.api.YangExpr;

/**
 * Transaction chain that additionally allows creation of read-only or read-write transactions with support for reading
 * of data using {@link YangExpr} XPath-s. Write-only transactions support only {@link YangInstanceIdentifier} paths.
 */
public final class XPathAwareTxChain extends TxChain implements NetconfXPathDOMTransactionChain {

    private final NetconfXPathDataBrokerExtension dataBrokerExtension;

    public XPathAwareTxChain(final DOMDataBroker dataBroker, final NetconfXPathDataBrokerExtension dataBrokerExtension,
                             final DOMTransactionChainListener listener) {
        super(dataBroker, listener);
        this.dataBrokerExtension = dataBrokerExtension;
    }

    @Override
    public synchronized XPathAwareDOMReadTransaction newXPathReadOnlyTransaction() {
        checkOperationPermitted();
        return null;
    }

    @Override
    public synchronized XPathAwareDOMReadWriteTransaction newXPathReadWriteTransaction() {
        return new XPathAwareReadWriteTx(dataBrokerExtension.newXPathReadOnlyTransaction(), newReadWriteTransaction());
    }
}