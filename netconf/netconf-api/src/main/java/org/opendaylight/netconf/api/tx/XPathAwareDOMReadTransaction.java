/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.tx;

import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.xpath.api.YangExpr;

/**
 * {@link DOMDataTreeReadTransaction} with added option to use XPath {@link YangExpr} in addition
 * to {@link YangInstanceIdentifier} for reading of data from data-store.
 */
public interface XPathAwareDOMReadTransaction extends DOMDataTreeReadTransaction, XPathReadOperations {
}