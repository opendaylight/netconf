/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.operations;

import java.util.Collections;
import java.util.Set;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.impl.SessionIdProvider;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;


public class OperationsProvider implements NetconfOperationServiceFactory {

    private final Set<Capability> caps;
    private final SessionIdProvider idProvider;
    private final OperationsCreator operationsCreator;

    public OperationsProvider(final SessionIdProvider idProvider,
        final Set<Capability> caps) {
        this(idProvider, caps,
            DefaultOperationsCreator.getDefaultOperationServiceCreator(idProvider.getCurrentSessionId()));
    }

    public OperationsProvider(final SessionIdProvider idProvider,
        final Set<Capability> caps, OperationsCreator operationsCreator) {
        this.caps = caps;
        this.idProvider = idProvider;
        this.operationsCreator = operationsCreator;
    }

    @Override
    public Set<Capability> getCapabilities() {
        return caps;
    }

    @Override
    public AutoCloseable registerCapabilityListener(
        final CapabilityListener listener) {
        listener.onCapabilitiesChanged(caps, Collections.<Capability>emptySet());
        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
            }
        };
    }

    @Override
    public NetconfOperationService createService(
        final String netconfSessionIdForReporting) {
        return operationsCreator.getNetconfOperationService(caps, idProvider, netconfSessionIdForReporting);
    }
}
