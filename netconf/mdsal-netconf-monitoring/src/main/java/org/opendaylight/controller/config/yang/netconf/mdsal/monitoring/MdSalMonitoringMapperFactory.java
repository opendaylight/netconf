/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.netconf.mdsal.monitoring;

import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.controller.sal.common.util.NoopAutoCloseable;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactoryListener;
import org.opendaylight.netconf.monitoring.GetSchema;

import java.util.Collections;
import java.util.Set;

public class MdSalMonitoringMapperFactory implements NetconfOperationServiceFactory, AutoCloseable {

    private final NetconfOperationService operationService;
    private final MonitoringToMdsalWriter monitoringToMdsalWriter;
    private final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener;

    private static final Set<Capability> CAPABILITIES = Collections.emptySet();

    public MdSalMonitoringMapperFactory(final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener,
                                        final NetconfMonitoringService netconfMonitoringService,
                                        final MonitoringToMdsalWriter monitoringToMdsalWriter) {

        this.netconfOperationServiceFactoryListener = netconfOperationServiceFactoryListener;
        this.monitoringToMdsalWriter = monitoringToMdsalWriter;

        this.operationService = new NetconfOperationService() {
            @Override
            public Set<NetconfOperation> getNetconfOperations() {
                return Collections.singleton(new GetSchema(netconfMonitoringService));
            }

            @Override
            public void close() {
                // NOOP
            }
        };

        this.netconfOperationServiceFactoryListener.onAddNetconfOperationServiceFactory(this);
    }

    @Override
    public NetconfOperationService createService(final String netconfSessionIdForReporting) {
        return operationService;
    }

    @Override
    public Set<Capability> getCapabilities() {
        // TODO
        // No capabilities exposed to prevent clashes with schemas from mdsal-netconf-connector (it exposes all the schemas)
        // If the schemas exposed by mdsal-netconf-connector are filtered, this class would expose monitoring related models
        return CAPABILITIES;
    }

    @Override
    public AutoCloseable registerCapabilityListener(final CapabilityListener listener) {
        return NoopAutoCloseable.INSTANCE;
    }

    /**
     * Invoke using blueprint
     */
    @Override
    public void close() {
        monitoringToMdsalWriter.close();
        netconfOperationServiceFactoryListener.onRemoveNetconfOperationServiceFactory(this);
    }

}