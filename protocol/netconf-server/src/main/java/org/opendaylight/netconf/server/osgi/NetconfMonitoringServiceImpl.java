/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.osgi;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
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
    private final ScheduledExecutorService executorService;

    private NetconfMonitoringServiceImpl(final NetconfOperationServiceFactory opProvider,
            final NetconfSessionMonitoringService sessionMonitoring) {
        capabilityMonitoring = new NetconfCapabilityMonitoringService(opProvider);
        this.sessionMonitoring = requireNonNull(sessionMonitoring);
        executorService = null;
    }

    public NetconfMonitoringServiceImpl(final NetconfOperationServiceFactory opProvider) {
        this(opProvider, new NetconfSessionMonitoringService.WithoutUpdates());
    }

    public NetconfMonitoringServiceImpl(final NetconfOperationServiceFactory opProvider,
            final ThreadFactory threadFactory, final long period, final TimeUnit timeUnit) {
        capabilityMonitoring = new NetconfCapabilityMonitoringService(opProvider);
        if (period > 0) {
            executorService = Executors.unconfigurableScheduledExecutorService(
                // Note: 0 core pool size, as we want to shut the thread down when we do not have listeners
                Executors.newScheduledThreadPool(0, threadFactory));
            sessionMonitoring = new NetconfSessionMonitoringService.WithUpdates(executorService, period, timeUnit);
        } else {
            executorService = null;
            sessionMonitoring = new NetconfSessionMonitoringService.WithoutUpdates();
        }
    }

    public NetconfMonitoringServiceImpl(final NetconfOperationServiceFactory opProvider,
            final ScheduledExecutorService threadPool, final long periodSeconds) {
        this(opProvider, periodSeconds > 0
            ? new NetconfSessionMonitoringService.WithUpdates(threadPool, periodSeconds, TimeUnit.SECONDS)
                : new NetconfSessionMonitoringService.WithoutUpdates());
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
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
