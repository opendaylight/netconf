/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.Datastore;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class Get extends AbstractGet {

    private static final String OPERATION_NAME = "get";


    public Get(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext,
               final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting, schemaContext, transactionProvider);
    }

    @Override
    Datastore getDatastore(final XmlElement operationElement) {
        return Datastore.running;
    }

    @Override
    CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(final DOMDataReadWriteTransaction rwTx,
                                                                            final YangInstanceIdentifier path) {
        return rwTx.read(LogicalDatastoreType.OPERATIONAL, path);
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
