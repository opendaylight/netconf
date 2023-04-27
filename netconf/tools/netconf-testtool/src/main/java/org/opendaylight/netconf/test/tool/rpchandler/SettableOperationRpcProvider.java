/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.rpchandler;

import java.util.Set;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.yangtools.concepts.Registration;

public class SettableOperationRpcProvider implements NetconfOperationServiceFactory {
    private final RpcHandler rpcHandler;

    public SettableOperationRpcProvider(final RpcHandler rpcHandler) {
        this.rpcHandler = rpcHandler;
    }

    @Override
    public Set<Capability> getCapabilities() {
        return Set.of();
    }

    @Override
    public Registration registerCapabilityListener(final CapabilityListener listener) {
        return () -> { };
    }

    @Override
    public NetconfOperationService createService(final String netconfSessionIdForReporting) {
        return new SettableOperationService(rpcHandler);
    }

    private static class SettableOperationService implements NetconfOperationService {
        private final SettableRpc rpc;

        SettableOperationService(final RpcHandler rpcHandler) {
            rpc = new SettableRpc(rpcHandler);
        }

        @Override
        public Set<NetconfOperation> getNetconfOperations() {
            return Set.of(rpc);
        }

        @Override
        public void close() {
            // no op
        }
    }
}
