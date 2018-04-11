/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector;

import java.util.Set;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;

public class MdsalNetconfOperationService implements NetconfOperationService {

    private final OperationProvider operationProvider;

    public MdsalNetconfOperationService(final CurrentSchemaContext schemaContext,
                                        final String netconfSessionIdForReporting,
                                        final NetconfDataBroker dataBroker, final DOMRpcService rpcService) {
        this.operationProvider = new OperationProvider(netconfSessionIdForReporting, schemaContext, dataBroker,
                rpcService);
    }

    @Override
    public void close() {

    }

    @Override
    public Set<NetconfOperation> getNetconfOperations() {
        return operationProvider.getOperations();
    }

}
