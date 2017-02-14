/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.osgi;

import java.util.Collection;
import java.util.Optional;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfConfigUtil {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfConfigUtil.class);

    private NetconfConfigUtil() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    public static NetconfConfiguration getNetconfConfigurationService(BundleContext bundleContext)
            throws InvalidSyntaxException {
        LOG.debug("Trying to retrieve netconf configuration service");
        final Collection<ServiceReference<ManagedService>> serviceReferences
                = bundleContext.getServiceReferences(ManagedService.class, null);
        for (final ServiceReference<ManagedService> serviceReference : serviceReferences) {
                ManagedService service = bundleContext.getService(serviceReference);
                if (service instanceof NetconfConfiguration){
                    LOG.debug("Netconf configuration service found");
                    return (NetconfConfiguration) service;
                }
        }

        throw new IllegalStateException("Netconf configuration service not found");
    }
}
