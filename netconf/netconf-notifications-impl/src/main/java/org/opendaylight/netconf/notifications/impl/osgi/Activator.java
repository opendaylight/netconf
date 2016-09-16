/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.notifications.impl.osgi;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Set;
import org.opendaylight.controller.config.util.capability.BasicCapability;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.api.util.NetconfConstants;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.netconf.notifications.NetconfNotificationCollector;
import org.opendaylight.netconf.notifications.impl.NetconfNotificationManager;
import org.opendaylight.netconf.notifications.impl.ops.CreateSubscription;
import org.opendaylight.netconf.notifications.impl.ops.Get;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator {

    private ServiceRegistration<NetconfNotificationCollector> netconfNotificationCollectorServiceRegistration;
    private ServiceRegistration<NetconfOperationServiceFactory> operationaServiceRegistration;

    private final BundleContext context;
    private final NetconfNotificationManager netconfNotificationManager;

    public Activator(final BundleContext context, final NetconfNotificationManager netconfNotificationManager) {
        this.context = context;
        this.netconfNotificationManager = netconfNotificationManager;
    }

    /**
     * Invoke by blueprint
     */
    public void start() {
        // Add properties to autowire with netconf-impl instance for cfg subsystem
        final Dictionary<String, String> props = new Hashtable<>();
        props.put(NetconfConstants.SERVICE_NAME, NetconfConstants.NETCONF_NOTIFICATION);
        netconfNotificationCollectorServiceRegistration = context.registerService(NetconfNotificationCollector.class, netconfNotificationManager, new Hashtable<String, Object>());

        final NetconfOperationServiceFactory netconfOperationServiceFactory = new NetconfOperationServiceFactory() {

            private final Set<Capability> capabilities = Collections.<Capability>singleton(new BasicCapability(NetconfNotification.NOTIFICATION_NAMESPACE));

            @Override
            public Set<Capability> getCapabilities() {
                return capabilities;
            }

            @Override
            public AutoCloseable registerCapabilityListener(final CapabilityListener listener) {
                listener.onCapabilitiesChanged(capabilities, Collections.<Capability>emptySet());
                return () -> listener.onCapabilitiesChanged(Collections.<Capability>emptySet(), capabilities);
            }

            @Override
            public NetconfOperationService createService(final String netconfSessionIdForReporting) {
                return new NetconfOperationService() {

                    private final CreateSubscription createSubscription = new CreateSubscription(netconfSessionIdForReporting, netconfNotificationManager);

                    @Override
                    public Set<NetconfOperation> getNetconfOperations() {
                        return Sets.<NetconfOperation>newHashSet(
                                new Get(netconfSessionIdForReporting, netconfNotificationManager),
                                createSubscription);
                    }

                    @Override
                    public void close() {
                        createSubscription.close();
                    }
                };
            }
        };

        final Dictionary<String, String> properties = new Hashtable<>();
        properties.put(NetconfConstants.SERVICE_NAME, NetconfConstants.NETCONF_MONITORING);
        operationaServiceRegistration = context.registerService(NetconfOperationServiceFactory.class, netconfOperationServiceFactory, properties);
    }

    /**
     * Invoke by blueprint
     */
    public void stop() {
        if(netconfNotificationCollectorServiceRegistration != null) {
            netconfNotificationCollectorServiceRegistration.unregister();
            netconfNotificationCollectorServiceRegistration = null;
        }
        if (operationaServiceRegistration != null) {
            operationaServiceRegistration.unregister();
            operationaServiceRegistration = null;
        }
    }
}
