/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.tx;

import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.xpath.api.YangExpr;

/**
 * This interface specifies factory methods for creation of transactions that support both
 * {@link YangInstanceIdentifier} and {@link YangExpr} for specifications of paths to resources.
 */
public interface NetconfXpathDataBrokerOperations {

    /**
     * Creation of read-only transaction that allows reading of data from NETCONF device using both
     * {@link YangInstanceIdentifier} and {@link YangExpr}.
     *
     * @return Read-only transaction.
     */
    XPathAwareDOMReadTransaction newXPathReadOnlyTransaction();

    /**
     * Creation of read/write transaction that allows writing of data to NETCONF device using
     * {@link YangInstanceIdentifier} and reading of data from NETCONF device using both {@link YangInstanceIdentifier}
     * and {@link YangExpr}.
     *
     * @return Read/write transaction.
     */
    XPathAwareDOMReadWriteTransaction newXPathReadWriteTransaction();
}