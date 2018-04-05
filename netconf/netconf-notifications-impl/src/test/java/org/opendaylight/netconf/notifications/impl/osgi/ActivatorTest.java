/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.notifications.impl.osgi;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Set;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opendaylight.netconf.api.capability.BasicCapability;
import org.opendaylight.netconf.api.capability.Capability;
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

public class ActivatorTest {

    @Test
    public void testActivator() throws Exception {
        final Activator activator = new Activator();
        final BundleContext context = mock(BundleContext.class);


        final ServiceRegistration netconfNotificationCollectorServiceRegistration  = mock(ServiceRegistration.class);
        final ServiceRegistration operationaServiceRegistration = mock(ServiceRegistration.class);

        // test registering services
        doReturn(netconfNotificationCollectorServiceRegistration).when(context)
                .registerService(eq(NetconfNotificationCollector.class), any(NetconfNotificationManager.class), any());
        doReturn(operationaServiceRegistration).when(context).registerService(eq(NetconfOperationServiceFactory.class),
                any(NetconfOperationServiceFactory.class), any());

        activator.start(context);

        verify(context, times(1)).registerService(eq(NetconfNotificationCollector.class),
                any(NetconfNotificationManager.class), eq(new Hashtable<>()));

        final ArgumentCaptor<NetconfOperationServiceFactory> serviceFactoryArgumentCaptor =
                ArgumentCaptor.forClass(NetconfOperationServiceFactory.class);

        final Dictionary<String, String> properties = new Hashtable<>();
        properties.put(NetconfConstants.SERVICE_NAME, NetconfConstants.NETCONF_MONITORING);

        verify(context, times(1)).registerService(eq(NetconfOperationServiceFactory.class),
                serviceFactoryArgumentCaptor.capture(), eq(properties));

        // test service factory argument requisites
        final NetconfOperationServiceFactory serviceFactory = serviceFactoryArgumentCaptor.getValue();

        final Set<Capability> capabilities =
                Collections.singleton(new BasicCapability(NetconfNotification.NOTIFICATION_NAMESPACE));

        assertEquals(capabilities.iterator().next()
                .getCapabilityUri(), serviceFactory.getCapabilities().iterator().next().getCapabilityUri());
        assertEquals(capabilities.iterator().next()
                .getCapabilitySchema(), serviceFactory.getCapabilities().iterator().next().getCapabilitySchema());
        assertEquals(capabilities.iterator().next()
                .getModuleNamespace(), serviceFactory.getCapabilities().iterator().next().getModuleNamespace());
        assertEquals(capabilities.iterator().next()
                .getModuleName(), serviceFactory.getCapabilities().iterator().next().getModuleName());

        final CapabilityListener listener = mock(CapabilityListener.class);

        doNothing().when(listener).onCapabilitiesChanged(any(), any());

        serviceFactory.registerCapabilityListener(listener);

        verify(listener).onCapabilitiesChanged(serviceFactory.getCapabilities(), Collections.emptySet());

        final NetconfOperationService netconfOperationService = serviceFactory.createService("id");
        final Set<NetconfOperation> netconfOperations = netconfOperationService.getNetconfOperations();

        final CreateSubscription createSubscription =
                new CreateSubscription("id", activator.getNetconfNotificationManager());

        netconfOperations.forEach(
            operation -> {
                if (operation instanceof CreateSubscription) {
                    assertEquals(createSubscription.toString(), operation.toString());
                }
                if (operation instanceof Get) {
                    assertEquals("id", ((Get) operation).getNetconfSessionIdForReporting());
                }
            }
        );

        // test unregister after stop
        doNothing().when(netconfNotificationCollectorServiceRegistration).unregister();
        doNothing().when(operationaServiceRegistration).unregister();

        activator.stop(context);

        verify(netconfNotificationCollectorServiceRegistration, times(1)).unregister();
        verify(operationaServiceRegistration, times(1)).unregister();

    }


}
