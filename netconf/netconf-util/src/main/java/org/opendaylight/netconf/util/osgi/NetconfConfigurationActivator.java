/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.osgi;

import java.util.Hashtable;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfConfigurationActivator implements BundleActivator {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfConfigurationActivator.class);

    // This has to match netconf config filename without .cfg suffix
    private static final String CONFIG_PID = "netconf";
    private static final Hashtable<String, String> PROPS = new Hashtable<>(1);

    static {
        PROPS.put(Constants.SERVICE_PID, CONFIG_PID);
    }

    private ServiceRegistration<?> configService;

    @Override
    public void start(final BundleContext bundleContext) {
        LOG.debug("Starting netconf configuration service");
        configService = bundleContext.registerService(ManagedService.class,
                new NetconfConfiguration(), PROPS);
    }

    @Override
    public void stop(final BundleContext bundleContext) {
        if (configService != null) {
            LOG.debug("Unregistering netconf configuration service");
            configService.unregister();
            configService = null;
        }
    }
}
