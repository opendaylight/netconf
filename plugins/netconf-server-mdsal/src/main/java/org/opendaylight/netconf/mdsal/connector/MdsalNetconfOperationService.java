/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector;

import com.google.common.collect.ImmutableSet;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
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
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;

final class MdsalNetconfOperationService implements NetconfOperationService {
    private final ImmutableSet<NetconfOperation> operations;
    private final TransactionProvider transactionProvider;

    MdsalNetconfOperationService(final CurrentSchemaContext schemaContext, final SessionIdType sessionId,
            final DOMDataBroker dataBroker, final DOMRpcService rpcService) {
        transactionProvider = new TransactionProvider(dataBroker, sessionId);
        operations = ImmutableSet.of(
            new Commit(sessionId, transactionProvider),
            new DiscardChanges(sessionId, transactionProvider),
            new EditConfig(sessionId, schemaContext, transactionProvider),
            new CopyConfig(sessionId, schemaContext, transactionProvider),
            new Get(sessionId, schemaContext, transactionProvider),
            new GetConfig(sessionId, schemaContext, transactionProvider),
            new Lock(sessionId),
            new Unlock(sessionId),
            new RuntimeRpc(sessionId, schemaContext, rpcService),
            new Validate(sessionId, transactionProvider));
    }

    @Override
    public ImmutableSet<NetconfOperation> getNetconfOperations() {
        return operations;
    }

    @Override
    public void close() {
        // No-op
    }
}
