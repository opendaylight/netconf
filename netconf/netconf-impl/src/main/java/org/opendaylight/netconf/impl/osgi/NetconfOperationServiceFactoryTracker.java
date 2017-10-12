/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import org.opendaylight.netconf.api.util.NetconfConstants;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactoryListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

class NetconfOperationServiceFactoryTracker extends
        ServiceTracker<NetconfOperationServiceFactory, NetconfOperationServiceFactory> {
    private final NetconfOperationServiceFactoryListener factoriesListener;

    NetconfOperationServiceFactoryTracker(final BundleContext context,
                                          final NetconfOperationServiceFactoryListener factoriesListener) {
        super(context, NetconfOperationServiceFactory.class, null);
        this.factoriesListener = factoriesListener;
    }

    @Override
    public NetconfOperationServiceFactory addingService(final ServiceReference<NetconfOperationServiceFactory> reference) {
        Object property = reference.getProperty(NetconfConstants.SERVICE_NAME);
        if (property != null && isMarkedForConfig(property)) {
            NetconfOperationServiceFactory netconfOperationServiceFactory = super.addingService(reference);
            factoriesListener.onAddNetconfOperationServiceFactory(netconfOperationServiceFactory);
            return netconfOperationServiceFactory;
        }

        return null;
    }

    @Override
    public void removedService(final ServiceReference<NetconfOperationServiceFactory> reference,
                               final NetconfOperationServiceFactory netconfOperationServiceFactory) {
        if (netconfOperationServiceFactory != null) {
            factoriesListener.onRemoveNetconfOperationServiceFactory(netconfOperationServiceFactory);
        }
    }

    private static boolean isMarkedForConfig(final Object property) {
        return NetconfConstants.CONFIG_SERVICE_MARKERS.contains(property);
    }

}
