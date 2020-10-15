/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx.fields;

import com.google.common.util.concurrent.FluentFuture;
import java.util.List;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadTransaction;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadWriteTransaction;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.ReadWriteTx;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public final class FieldsAwareReadWriteTx extends ReadWriteTx<NetconfDOMFieldsReadTransaction>
        implements NetconfDOMFieldsReadWriteTransaction {

    public FieldsAwareReadWriteTx(final NetconfDOMFieldsReadTransaction readTransaction,
                                  final DOMDataTreeWriteTransaction writeTransaction) {
        super(readTransaction, writeTransaction);
    }

    @Override
    public FluentFuture<Optional<NormalizedNode<?, ?>>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        return delegateReadTx.read(store, path, fields);
    }
}