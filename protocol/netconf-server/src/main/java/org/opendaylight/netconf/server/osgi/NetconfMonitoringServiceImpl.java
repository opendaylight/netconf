/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.osgi;

import java.util.Optional;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.SessionListener;
import org.opendaylight.netconf.server.api.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yangtools.concepts.Registration;

public class NetconfMonitoringServiceImpl implements NetconfMonitoringService, AutoCloseable {
    private final NetconfCapabilityMonitoringService capabilityMonitoring;
    private final NetconfSessionMonitoringService sessionMonitoring;

    public NetconfMonitoringServiceImpl(final NetconfOperationServiceFactory opProvider) {
        this(opProvider, Optional.empty(), 0);
    }

    public NetconfMonitoringServiceImpl(final NetconfOperationServiceFactory opProvider,
                                        final ScheduledThreadPool threadPool,
                                        final long updateInterval) {
        this(opProvider, Optional.ofNullable(threadPool), updateInterval);
    }

    public NetconfMonitoringServiceImpl(final NetconfOperationServiceFactory opProvider,
                                        final Optional<ScheduledThreadPool> threadPool,
                                        final long updateInterval) {
        capabilityMonitoring = new NetconfCapabilityMonitoringService(opProvider);
        sessionMonitoring = new NetconfSessionMonitoringService(threadPool, updateInterval);

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
    public String getSchemaForCapability(final String moduleName, final Optional<String> revision) {
        return capabilityMonitoring.getSchemaForModuleRevision(moduleName, revision);
    }

    @Override
    public Capabilities getCapabilities() {
        return capabilityMonitoring.getCapabilities();
    }

    @Override
    public Registration registerCapabilitiesListener(final CapabilitiesListener listener) {
        return capabilityMonitoring.registerListener(listener);
    }

    @Override
    public Registration registerSessionsListener(final SessionsListener listener) {
        return sessionMonitoring.registerListener(listener);
    }

    public void setNotificationPublisher(final BaseNotificationPublisherRegistration notificationPublisher) {
        capabilityMonitoring.setNotificationPublisher(notificationPublisher);
    }

    @Override
    public void close() {
        capabilityMonitoring.close();
        sessionMonitoring.close();
    }
}
