/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.netconf.mdsal.monitoring;

import java.util.Collections;
import java.util.Set;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactoryListener;

public class MdsalMonitoringMapperFactory implements NetconfOperationServiceFactory, AutoCloseable {

    private final MonitoringToMdsalWriter monitoringToMdsalWriter;
    private final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener;
    private final NetconfMonitoringService netconfMonitoringService;

    private static final Set<Capability> CAPABILITIES = Collections.emptySet();

    public MdsalMonitoringMapperFactory(
            final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener,
            final NetconfMonitoringService netconfMonitoringService,
            final MonitoringToMdsalWriter monitoringToMdsalWriter) {

        this.netconfOperationServiceFactoryListener = netconfOperationServiceFactoryListener;
        this.netconfMonitoringService = netconfMonitoringService;
        this.monitoringToMdsalWriter = monitoringToMdsalWriter;
        this.netconfOperationServiceFactoryListener.onAddNetconfOperationServiceFactory(this);
    }

    @Override
    public NetconfOperationService createService(final String netconfSessionIdForReporting) {
        return new NetconfOperationService() {
            @Override
            public Set<NetconfOperation> getNetconfOperations() {
                return Collections.singleton(new GetSchema(netconfSessionIdForReporting, netconfMonitoringService));
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
        return CAPABILITIES;
    }

    @Override
    public AutoCloseable registerCapabilityListener(final CapabilityListener listener) {
        return () -> {};
    }

    /**
     * Invoked using blueprint.
     */
    @Override
    public void close() {
        monitoringToMdsalWriter.close();
        netconfOperationServiceFactoryListener.onRemoveNetconfOperationServiceFactory(this);
    }

}
