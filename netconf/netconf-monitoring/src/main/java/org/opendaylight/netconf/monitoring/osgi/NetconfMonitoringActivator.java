/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.monitoring.osgi;

import java.util.Collections;
import java.util.Set;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfMonitoringActivator {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfMonitoringActivator.class);

    private final BundleContext context;

    private NetconfMonitoringServiceTracker monitor;

    public NetconfMonitoringActivator(final BundleContext bundleContext) {
        this.context = bundleContext;
    }
    /**
     * Invoke by blueprint
     */
    public void start()  {
        monitor = new NetconfMonitoringServiceTracker(context);
        monitor.open();
    }

    /**
     * Invoke by blueprint
     */
    public void stop() {
        if(monitor!=null) {
            try {
                monitor.close();
            } catch (final Exception e) {
                LOG.warn("Ignoring exception while closing {}", monitor, e);
            }
        }
    }

    public static class NetconfMonitoringOperationServiceFactory implements NetconfOperationServiceFactory, AutoCloseable {

        private final NetconfMonitoringOperationService operationService;

        public NetconfMonitoringOperationServiceFactory(final NetconfMonitoringOperationService operationService) {
            this.operationService = operationService;
        }

        @Override
        public NetconfOperationService createService(final String netconfSessionIdForReporting) {
            return operationService;
        }

        @Override
        public Set<Capability> getCapabilities() {
            return Collections.emptySet();
        }

        @Override
        public AutoCloseable registerCapabilityListener(final CapabilityListener listener) {
            return () -> {
                // NOOP
            };
        }

        @Override
        public void close() {}
    }
}
