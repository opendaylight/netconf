/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collections;
import java.util.Set;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.impl.SessionIdProvider;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.test.tool.rpc.DataList;
import org.opendaylight.netconf.test.tool.rpc.SimulatedCommit;
import org.opendaylight.netconf.test.tool.rpc.SimulatedCreateSubscription;
import org.opendaylight.netconf.test.tool.rpc.SimulatedDiscardChanges;
import org.opendaylight.netconf.test.tool.rpc.SimulatedEditConfig;
import org.opendaylight.netconf.test.tool.rpc.SimulatedGet;
import org.opendaylight.netconf.test.tool.rpc.SimulatedGetConfig;
import org.opendaylight.netconf.test.tool.rpc.SimulatedLock;
import org.opendaylight.netconf.test.tool.rpc.SimulatedUnLock;

class SimulatedOperationProvider implements NetconfOperationServiceFactory {
    private final Set<Capability> caps;
    private final SimulatedOperationService simulatedOperationService;

    SimulatedOperationProvider(final SessionIdProvider idProvider,
                               final Set<Capability> caps,
                               final Optional<File> notificationsFile,
                               final Optional<File> initialConfigXMLFile) {
        this.caps = caps;
        simulatedOperationService = new SimulatedOperationService(
            idProvider.getCurrentSessionId(), notificationsFile, initialConfigXMLFile);
    }

    @Override
    public Set<Capability> getCapabilities() {
        return caps;
    }

    @Override
    public AutoCloseable registerCapabilityListener(
            final CapabilityListener listener) {
        listener.onCapabilitiesChanged(caps, Collections.emptySet());
        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
            }
        };
    }

    @Override
    public NetconfOperationService createService(
            final String netconfSessionIdForReporting) {
        return simulatedOperationService;
    }

    static class SimulatedOperationService implements NetconfOperationService {
        private final long currentSessionId;
        private final Optional<File> notificationsFile;
        private final Optional<File> initialConfigXMLFile;

        SimulatedOperationService(final long currentSessionId, final Optional<File> notificationsFile,
                                  final Optional<File> initialConfigXMLFile) {
            this.currentSessionId = currentSessionId;
            this.notificationsFile = notificationsFile;
            this.initialConfigXMLFile = initialConfigXMLFile;
        }

        @Override
        public Set<NetconfOperation> getNetconfOperations() {
            final DataList storage = new DataList();
            final SimulatedGet sGet = new SimulatedGet(String.valueOf(currentSessionId), storage);
            final SimulatedEditConfig sEditConfig = new SimulatedEditConfig(String.valueOf(currentSessionId), storage);
            final SimulatedGetConfig sGetConfig = new SimulatedGetConfig(
                String.valueOf(currentSessionId), storage, initialConfigXMLFile);
            final SimulatedCommit sCommit = new SimulatedCommit(String.valueOf(currentSessionId));
            final SimulatedLock sLock = new SimulatedLock(String.valueOf(currentSessionId));
            final SimulatedUnLock sUnlock = new SimulatedUnLock(String.valueOf(currentSessionId));
            final SimulatedCreateSubscription sCreateSubs = new SimulatedCreateSubscription(
                    String.valueOf(currentSessionId), notificationsFile);
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
