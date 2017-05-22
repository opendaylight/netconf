/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.Datastore;

public class Get extends AbstractGet {

    private static final String OPERATION_NAME = "get";

    public Get(String netconfSessionIdForReporting, CurrentSchemaContext schemaContext,
               TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting, schemaContext, transactionProvider, LogicalDatastoreType.OPERATIONAL);
    }

    @Override
    Datastore getNetconfDatastore(XmlElement operationElement) {
        return Datastore.running;
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
