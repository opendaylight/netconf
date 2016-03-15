/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.netconf.mdsal.notification;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.mdsal.notification.NetconfNotificationOperationServiceFactory;
import org.opendaylight.netconf.notifications.NetconfNotificationCollector;
import org.opendaylight.yangtools.concepts.ListenerRegistration;

public class NetconfMdsalNotificationMapperModule extends org.opendaylight.controller.config.yang.netconf.mdsal.notification.AbstractNetconfMdsalNotificationMapperModule {
    public NetconfMdsalNotificationMapperModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public NetconfMdsalNotificationMapperModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.controller.config.yang.netconf.mdsal.notification.NetconfMdsalNotificationMapperModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        final NetconfNotificationCollector notificationCollector = getNotificationCollectorDependency();

        final NotificationToMdsalWriter notificationToMdsalWriter = new NotificationToMdsalWriter(notificationCollector);
        getBindingAwareBrokerDependency().registerProvider(notificationToMdsalWriter);
        final DataBroker dataBroker = getDataBrokerDependency();

        final OperationalDatastoreListener capabilityNotificationProducer =
                new CapabilityChangeNotificationProducer(notificationCollector.registerBaseNotificationPublisher());
        final ListenerRegistration capabilityChangeListenerRegistration = capabilityNotificationProducer.registerOnChanges(dataBroker);

        final OperationalDatastoreListener sessionNotificationProducer =
                new SessionNotificationProducer(notificationCollector.registerBaseNotificationPublisher());
        final ListenerRegistration sessionListenerRegistration = sessionNotificationProducer.registerOnChanges(dataBroker);

        final NetconfNotificationOperationServiceFactory netconfNotificationOperationServiceFactory =
            new NetconfNotificationOperationServiceFactory(getNotificationRegistryDependency()) {
                @Override
                public void close() {
                    super.close();
                    notificationToMdsalWriter.close();
                    capabilityChangeListenerRegistration.close();
                    sessionListenerRegistration.close();
                    getAggregatorDependency().onRemoveNetconfOperationServiceFactory(this);
                }
            };

        getAggregatorDependency().onAddNetconfOperationServiceFactory(netconfNotificationOperationServiceFactory);

        return netconfNotificationOperationServiceFactory;
    }
}
