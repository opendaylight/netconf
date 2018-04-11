/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mdsal.connector.ops.Commit;
import org.opendaylight.netconf.mdsal.connector.ops.CopyConfig;
import org.opendaylight.netconf.mdsal.connector.ops.DiscardChanges;
import org.opendaylight.netconf.mdsal.connector.ops.EditConfig;
import org.opendaylight.netconf.mdsal.connector.ops.Lock;
import org.opendaylight.netconf.mdsal.connector.ops.RuntimeRpc;
import org.opendaylight.netconf.mdsal.connector.ops.Unlock;
import org.opendaylight.netconf.mdsal.connector.ops.Validate;
import org.opendaylight.netconf.mdsal.connector.ops.get.Get;
import org.opendaylight.netconf.mdsal.connector.ops.get.GetConfig;

final class OperationProvider {

    private final Set<NetconfOperation> operations;

    OperationProvider(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext,
                      final NetconfDataBroker dataBroker, final DOMRpcService rpcService) {
        final TransactionProvider transactionProvider = new TransactionProvider(dataBroker,
            netconfSessionIdForReporting);

        this.operations = ImmutableSet.of(
            new Commit(netconfSessionIdForReporting, transactionProvider),
            new DiscardChanges(netconfSessionIdForReporting, transactionProvider),
            new EditConfig(netconfSessionIdForReporting, schemaContext, transactionProvider),
            new CopyConfig(netconfSessionIdForReporting, schemaContext, transactionProvider),
            new Get(netconfSessionIdForReporting, schemaContext, transactionProvider),
            new GetConfig(netconfSessionIdForReporting, schemaContext, transactionProvider),
            new Lock(netconfSessionIdForReporting),
            new Unlock(netconfSessionIdForReporting),
            new RuntimeRpc(netconfSessionIdForReporting, schemaContext, rpcService),
            new Validate(netconfSessionIdForReporting, transactionProvider));
    }

    Set<NetconfOperation> getOperations() {
        return operations;
    }
}
