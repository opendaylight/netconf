/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound.monitoring;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactoryListener;
import org.opendaylight.netconf.server.mdsal.monitoring.GetSchema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
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
    public NetconfOperationService createService(final SessionIdType sessionId) {
        return new NetconfOperationService() {
            @Override
            public Set<NetconfOperation> getNetconfOperations() {
                return Set.of(new GetSchema(sessionId, netconfMonitoringService));
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
