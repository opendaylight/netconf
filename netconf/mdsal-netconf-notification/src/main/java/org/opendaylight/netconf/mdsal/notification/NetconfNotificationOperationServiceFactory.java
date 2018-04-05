/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.notification;

import java.util.Collections;
import java.util.Set;
import org.opendaylight.controller.sal.common.util.NoopAutoCloseable;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactoryListener;
import org.opendaylight.netconf.notifications.NetconfNotificationRegistry;

public class NetconfNotificationOperationServiceFactory implements NetconfOperationServiceFactory, AutoCloseable {

    private final NetconfNotificationRegistry netconfNotificationRegistry;
    private final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener;

    public NetconfNotificationOperationServiceFactory(
            final NetconfNotificationRegistry netconfNotificationRegistry,
            final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener) {
        this.netconfNotificationRegistry = netconfNotificationRegistry;
        this.netconfOperationServiceFactoryListener = netconfOperationServiceFactoryListener;

        this.netconfOperationServiceFactoryListener.onAddNetconfOperationServiceFactory(this);
    }

    @Override
    public Set<Capability> getCapabilities() {
        // TODO
        // No capabilities exposed to prevent clashes with schemas from
        // config-netconf-connector (it exposes all the schemas)
        // If the schemas exposed by config-netconf-connector are filtered,
        // this class would expose monitoring related models
        return Collections.emptySet();
    }

    @Override
    public NetconfOperationService createService(String netconfSessionIdForReporting) {
        return new NetconfNotificationOperationService(netconfSessionIdForReporting, netconfNotificationRegistry);
    }

    @Override
    public AutoCloseable registerCapabilityListener(final CapabilityListener listener) {
        return NoopAutoCloseable.INSTANCE;
    }

    @Override
    public void close() {
        this.netconfOperationServiceFactoryListener.onRemoveNetconfOperationServiceFactory(this);
    }
}
