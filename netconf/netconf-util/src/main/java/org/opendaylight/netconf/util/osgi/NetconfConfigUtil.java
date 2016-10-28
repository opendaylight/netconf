/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.osgi;

import java.util.Collection;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfConfigUtil {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfConfigUtil.class);

    private NetconfConfigUtil() {
    }

    public static java.util.Optional<NetconfConfiguration> getNetconfConfigurationService(BundleContext bundleContext) {
        final Collection<ServiceReference<ManagedService>> serviceReferences;
        try {
            serviceReferences = bundleContext.getServiceReferences(ManagedService.class, null);
            for (final ServiceReference<ManagedService> serviceReference : serviceReferences) {
                ManagedService service = bundleContext.getService(serviceReference);
                if (service instanceof NetconfConfiguration){
                    return java.util.Optional.of((NetconfConfiguration) service);
                }
            }
        } catch (InvalidSyntaxException e) {
            LOG.error("Unable to retrieve references for ManagedService: {}", e);
        }
        LOG.error("Unable to retrieve NetconfConfiguration service. Not found. Bundle netconf-util probably failed to load.");
        return java.util.Optional.empty();
    }
}
