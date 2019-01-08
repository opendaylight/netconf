/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import com.google.common.base.Optional;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.impl.SessionIdProvider;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.test.tool.rpc.SimulatedCreateSubscription;

class SimulatedOperationProvider implements NetconfOperationServiceFactory {

    private SessionIdProvider idProvider;
    private final Set<Capability> caps;
    private final Optional<File> notificationsFile;
    private final NetconfOperationServiceFactory delegateOperationServiceFactory;

    SimulatedOperationProvider(final SessionIdProvider idProvider,
                               final Set<Capability> caps,
                               final Optional<File> notificationsFile,
                               final NetconfOperationServiceFactory delegateOperationServiceFactory) {
        this.idProvider = idProvider;
        this.caps = caps;
        this.notificationsFile = notificationsFile;
        this.delegateOperationServiceFactory = delegateOperationServiceFactory;
    }

    @Override
    public Set<Capability> getCapabilities() {
        return caps;
    }

    @Override
    public AutoCloseable registerCapabilityListener(
            final CapabilityListener listener) {
        listener.onCapabilitiesChanged(caps, Collections.emptySet());
        return () -> {
        };
    }

    @Override
    public NetconfOperationService createService(
            final String netconfSessionIdForReporting) {
        return new SimulatedOperationService(
                idProvider.getCurrentSessionId(), notificationsFile,
                delegateOperationServiceFactory.createService(netconfSessionIdForReporting));
    }

    static class SimulatedOperationService implements NetconfOperationService {
        private final long currentSessionId;
        private final Optional<File> notificationsFile;
        private final NetconfOperationService delegateOperationService;

        SimulatedOperationService(final long currentSessionId, final Optional<File> notificationsFile,
                                         final NetconfOperationService delegateOperationService) {
            this.currentSessionId = currentSessionId;
            this.notificationsFile = notificationsFile;
            this.delegateOperationService = delegateOperationService;
        }

        @Override
        public Set<NetconfOperation> getNetconfOperations() {
            final SimulatedCreateSubscription sCreateSubs = new SimulatedCreateSubscription(
                    String.valueOf(currentSessionId), notificationsFile);

            final Set<NetconfOperation> operations = new HashSet<>();
            operations.addAll(delegateOperationService.getNetconfOperations());
            operations.add(sCreateSubs);
            return operations;
        }

        @Override
        public void close() {
        }

    }
}
