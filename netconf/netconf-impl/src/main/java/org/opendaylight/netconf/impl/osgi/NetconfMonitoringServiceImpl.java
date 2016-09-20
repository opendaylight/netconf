/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import com.google.common.base.Optional;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.api.monitoring.SessionListener;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;

public class NetconfMonitoringServiceImpl implements NetconfMonitoringService, AutoCloseable {

    private final NetconfCapabilityMonitoringService capabilityMonitoring;
    private final NetconfSessionMonitoringService sessionMonitoring;

    public NetconfMonitoringServiceImpl(NetconfOperationServiceFactory opProvider) {
        this(opProvider, Optional.absent(), 0);
    }

    public NetconfMonitoringServiceImpl(NetconfOperationServiceFactory opProvider,
                                        ScheduledThreadPool threadPool,
                                        long updateInterval) {
        this(opProvider, Optional.fromNullable(threadPool), updateInterval);
    }

    public NetconfMonitoringServiceImpl(NetconfOperationServiceFactory opProvider,
                                        Optional<ScheduledThreadPool> threadPool,
                                        long updateInterval) {
        this.capabilityMonitoring = new NetconfCapabilityMonitoringService(opProvider);
        this.sessionMonitoring = new NetconfSessionMonitoringService(threadPool, updateInterval);

    }

    @Override
    public Sessions getSessions() {
        return sessionMonitoring.getSessions();
    }

    @Override
    public SessionListener getSessionListener() {
        return sessionMonitoring;
    }

    @Override
    public Schemas getSchemas() {
        return capabilityMonitoring.getSchemas();
    }

    @Override
    public String getSchemaForCapability(String moduleName, Optional<String> revision) {
        return capabilityMonitoring.getSchemaForModuleRevision(moduleName, revision);
    }

    @Override
    public Capabilities getCapabilities() {
        return capabilityMonitoring.getCapabilities();
    }

    @Override
    public AutoCloseable registerCapabilitiesListener(CapabilitiesListener listener) {
        return capabilityMonitoring.registerListener(listener);
    }

    @Override
    public AutoCloseable registerSessionsListener(SessionsListener listener) {
        return sessionMonitoring.registerListener(listener);
    }

    public void setNotificationPublisher(BaseNotificationPublisherRegistration notificationPublisher) {
        this.capabilityMonitoring.setNotificationPublisher(notificationPublisher);
    }

    @Override
    public void close() throws Exception {
        capabilityMonitoring.close();
        sessionMonitoring.close();
    }
}
