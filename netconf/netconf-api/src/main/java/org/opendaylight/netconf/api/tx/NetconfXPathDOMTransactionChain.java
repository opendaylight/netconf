/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.tx;

import org.opendaylight.mdsal.dom.api.DOMTransactionChain;

/**
 * Transaction chain with support for creation of XPath-aware transactions.
 */
public interface NetconfXPathDOMTransactionChain extends DOMTransactionChain, NetconfXpathDataBrokerOperations {
}