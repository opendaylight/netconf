/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.netconf.mdsal.monitoring;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactoryListener;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(service = { })
public final class MdsalMonitoringMapperFactory implements NetconfOperationServiceFactory, AutoCloseable {
    private final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener;
    private final NetconfMonitoringService netconfMonitoringService;

    @Activate
    public MdsalMonitoringMapperFactory(
            @Reference(target = "(type=mapper-aggregator-registry)")
            final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener,
            @Reference(target = "(type=netconf-server-monitoring)")
            final NetconfMonitoringService netconfMonitoringService) {
        this.netconfOperationServiceFactoryListener = requireNonNull(netconfOperationServiceFactoryListener);
        this.netconfMonitoringService = requireNonNull(netconfMonitoringService);
        this.netconfOperationServiceFactoryListener.onAddNetconfOperationServiceFactory(this);
    }

    @Deactivate
    @Override
    public void close() {
        netconfOperationServiceFactoryListener.onRemoveNetconfOperationServiceFactory(this);
    }

    @Override
    public NetconfOperationService createService(final String netconfSessionIdForReporting) {
        return new NetconfOperationService() {
            @Override
            public Set<NetconfOperation> getNetconfOperations() {
                return Set.of(new GetSchema(netconfSessionIdForReporting, netconfMonitoringService));
            }

            @Override
            public void close() {
                // NOOP
            }
        };
    }

    @Override
    public Set<Capability> getCapabilities() {
        // TODO No capabilities exposed to prevent clashes with schemas from mdsal-netconf-connector (it exposes all the
        // schemas). If the schemas exposed by mdsal-netconf-connector are filtered, this class would expose monitoring
        // related models.
        return Set.of();
    }

    @Override
    public Registration registerCapabilityListener(final CapabilityListener listener) {
        return () -> { };
    }
}
