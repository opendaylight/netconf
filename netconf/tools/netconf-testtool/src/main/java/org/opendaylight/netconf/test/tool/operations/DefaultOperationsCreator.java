/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.operations;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import java.util.Set;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.netconf.impl.SessionIdProvider;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.test.tool.rpc.DataList;
import org.opendaylight.netconf.test.tool.rpc.SimulatedCommit;
import org.opendaylight.netconf.test.tool.rpc.SimulatedCreateSubscription;
import org.opendaylight.netconf.test.tool.rpc.SimulatedDiscardChanges;
import org.opendaylight.netconf.test.tool.rpc.SimulatedEditConfig;
import org.opendaylight.netconf.test.tool.rpc.SimulatedGet;
import org.opendaylight.netconf.test.tool.rpc.SimulatedGetConfig;
import org.opendaylight.netconf.test.tool.rpc.SimulatedLock;
import org.opendaylight.netconf.test.tool.rpc.SimulatedUnLock;

public class DefaultOperationsCreator implements OperationsCreator {

    private final SimulatedOperationService simulatedOperationService;

    private DefaultOperationsCreator(final long currentSessionId) {
        simulatedOperationService = new SimulatedOperationService(currentSessionId);
    }

    @Override
    public NetconfOperationService getNetconfOperationService(final Set<Capability> caps,
        final SessionIdProvider idProvider,
        final String netconfSessionIdForReporting) {
        return simulatedOperationService;
    }

    public static DefaultOperationsCreator getDefaultOperationServiceCreator(final long currentSessionId) {
        return new DefaultOperationsCreator(currentSessionId);
    }

    static class SimulatedOperationService implements NetconfOperationService {

        private final long currentSessionId;

        SimulatedOperationService(final long currentSessionId) {
            this.currentSessionId = currentSessionId;
        }

        @Override
        public Set<NetconfOperation> getNetconfOperations() {
            final DataList storage = new DataList();
            final SimulatedGet sGet = new SimulatedGet(String.valueOf(currentSessionId), storage);
            final SimulatedEditConfig sEditConfig = new SimulatedEditConfig(String.valueOf(currentSessionId), storage);
            final SimulatedGetConfig sGetConfig = new SimulatedGetConfig(
                String.valueOf(currentSessionId), storage, Optional.absent());
            final SimulatedCommit sCommit = new SimulatedCommit(String.valueOf(currentSessionId));
            final SimulatedLock sLock = new SimulatedLock(String.valueOf(currentSessionId));
            final SimulatedUnLock sUnlock = new SimulatedUnLock(String.valueOf(currentSessionId));
            final SimulatedCreateSubscription sCreateSubs = new SimulatedCreateSubscription(
                String.valueOf(currentSessionId), Optional.absent());
            final SimulatedDiscardChanges sDiscardChanges = new SimulatedDiscardChanges(
                String.valueOf(currentSessionId));
            return Sets.newHashSet(
                sGet, sGetConfig, sEditConfig, sCommit, sLock, sUnlock, sCreateSubs, sDiscardChanges);
        }

        @Override
        public void close() {
        }

    }
}
