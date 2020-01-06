/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.util.concurrent.FluentFuture;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.api.tx.XPathAwareDOMReadWriteTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.xpath.api.YangExpr;

/**
 * Read/write transaction that allows reading of data from DS using both {@link YangInstanceIdentifier}
 * and {@link YangExpr} that represents XPath expression.
 */
public final class XPathAwareReadWriteTx extends ReadWriteTx implements XPathAwareDOMReadWriteTransaction {

    public XPathAwareReadWriteTx(final XPathAwareReadOnlyTx delegatedRtx,
                                 final DOMDataTreeWriteTransaction delegatedWtx) {
        super(delegatedRtx, delegatedWtx);
    }

    @Override
    public FluentFuture<Optional<NormalizedNode<?, ?>>> read(final LogicalDatastoreType store, final YangExpr xpath) {
        return ((XPathAwareReadOnlyTx) delegateReadTx).read(store, xpath);
    }

    @Override
    public FluentFuture<Boolean> exists(final LogicalDatastoreType store, final YangExpr xpath) {
        return ((XPathAwareReadOnlyTx) delegateReadTx).exists(store, xpath);
    }
}