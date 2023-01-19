/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.monitoring;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.yangtools.concepts.Registration;

public class NetconfMonitoringOperationServiceFactory implements NetconfOperationServiceFactory, AutoCloseable {
    private final @NonNull NetconfMonitoringOperationService operationService;

    public NetconfMonitoringOperationServiceFactory(final NetconfMonitoringOperationService operationService) {
        this.operationService = requireNonNull(operationService);
    }

    @Override
    public NetconfOperationService createService(final String netconfSessionIdForReporting) {
        return operationService;
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
    public void close() {
        // No-op
    }
}

