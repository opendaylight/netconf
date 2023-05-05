/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.customrpc;

import java.io.File;
import java.util.Set;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.concepts.Registration;

public class SettableOperationProvider implements NetconfOperationServiceFactory {
    private final File rpcConfig;

    public SettableOperationProvider(final File rpcConfig) {
        this.rpcConfig = rpcConfig;
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
    public NetconfOperationService createService(final SessionIdType sessionId) {
        return new SettableOperationService(rpcConfig);
    }

    private static class SettableOperationService implements NetconfOperationService {
        private final SettableRpc rpc;

        SettableOperationService(final File rpcConfig) {
            rpc = new SettableRpc(rpcConfig);
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
