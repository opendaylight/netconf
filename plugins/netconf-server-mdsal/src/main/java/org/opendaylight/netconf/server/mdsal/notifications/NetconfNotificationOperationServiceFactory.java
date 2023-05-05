/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationRegistry;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactoryListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(service = NetconfOperationServiceFactory.class, immediate = true,
           property = "type=mdsal-netconf-notification")
public final class NetconfNotificationOperationServiceFactory implements NetconfOperationServiceFactory, AutoCloseable {
    private final NetconfNotificationRegistry notifManager;
    private final NetconfOperationServiceFactoryListener aggregatorRegistry;

    @Activate
    public NetconfNotificationOperationServiceFactory(
            @Reference(target = "(type=netconf-notification-manager)") final NetconfNotificationRegistry notifManager,
            @Reference(target = "(type=mapper-aggregator-registry)")
            final NetconfOperationServiceFactoryListener aggregatorRegistry) {
        this.notifManager = requireNonNull(notifManager);
        this.aggregatorRegistry = requireNonNull(aggregatorRegistry);
        this.aggregatorRegistry.onAddNetconfOperationServiceFactory(this);
    }

    @Deactivate
    @Override
    public void close() {
        aggregatorRegistry.onRemoveNetconfOperationServiceFactory(this);
    }

    @Override
    public Set<Capability> getCapabilities() {
        // TODO
        // No capabilities exposed to prevent clashes with schemas from
        // config-netconf-connector (it exposes all the schemas)
        // If the schemas exposed by config-netconf-connector are filtered,
        // this class would expose monitoring related models
        return Set.of();
    }

    @Override
    public NetconfOperationService createService(final SessionIdType sessionId) {
        return new NetconfNotificationOperationService(sessionId, notifManager);
    }

    @Override
    public Registration registerCapabilityListener(final CapabilityListener listener) {
        return () -> { };
    }
}
