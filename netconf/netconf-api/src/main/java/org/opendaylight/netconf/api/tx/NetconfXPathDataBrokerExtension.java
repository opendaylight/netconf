/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.tx;

import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.xpath.api.YangExpr;

/**
 *  XPath-aware extension to {@link DOMDataBroker} that can be used for creation of transactions that support both
 * {@link YangInstanceIdentifier} and {@link YangExpr} paths.
 */
public interface NetconfXPathDataBrokerExtension extends DOMDataBrokerExtension, NetconfXpathDataBrokerOperations {

    /**
     * Creation of the transaction chain with support for creation of {@link XPathAwareDOMReadTransaction} and
     * {@link XPathAwareDOMReadWriteTransaction}.
     *
     * @param listener Transaction chain event listener.
     * @return Transaction chain.
     */
    NetconfXPathDOMTransactionChain createXpathTransactionChain(DOMTransactionChainListener listener);
}