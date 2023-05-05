/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.operations;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.Set;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.test.tool.rpc.DataList;
import org.opendaylight.netconf.test.tool.rpc.SimulatedCommit;
import org.opendaylight.netconf.test.tool.rpc.SimulatedCreateSubscription;
import org.opendaylight.netconf.test.tool.rpc.SimulatedDiscardChanges;
import org.opendaylight.netconf.test.tool.rpc.SimulatedEditConfig;
import org.opendaylight.netconf.test.tool.rpc.SimulatedGet;
import org.opendaylight.netconf.test.tool.rpc.SimulatedGetConfig;
import org.opendaylight.netconf.test.tool.rpc.SimulatedLock;
import org.opendaylight.netconf.test.tool.rpc.SimulatedUnLock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;

public final class DefaultOperationsCreator implements OperationsCreator {

    @Override
    public NetconfOperationService getNetconfOperationService(final Set<Capability> caps,
            final SessionIdType sessionId) {
        return new SimulatedOperationService(sessionId);
    }

    static class SimulatedOperationService implements NetconfOperationService {
        private final SessionIdType currentSessionId;

        SimulatedOperationService(final SessionIdType currentSessionId) {
            this.currentSessionId = requireNonNull(currentSessionId);
        }

        @Override
        public Set<NetconfOperation> getNetconfOperations() {
            final DataList storage = new DataList();
            final SimulatedGet sGet = new SimulatedGet(currentSessionId, storage);
            final SimulatedEditConfig sEditConfig = new SimulatedEditConfig(currentSessionId, storage);
            final SimulatedGetConfig sGetConfig = new SimulatedGetConfig(currentSessionId, storage, Optional.empty());
            final SimulatedCommit sCommit = new SimulatedCommit(currentSessionId);
            final SimulatedLock sLock = new SimulatedLock(currentSessionId);
            final SimulatedUnLock sUnlock = new SimulatedUnLock(currentSessionId);
            final SimulatedCreateSubscription sCreateSubs = new SimulatedCreateSubscription(currentSessionId,
                Optional.empty());
            final SimulatedDiscardChanges sDiscardChanges = new SimulatedDiscardChanges(currentSessionId);
            return Set.of(sGet, sGetConfig, sEditConfig, sCommit, sLock, sUnlock, sCreateSubs, sDiscardChanges);
        }

        @Override
        public void close() {
        }
    }
}
