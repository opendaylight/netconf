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

public class NetconfConfigurationActivator implements BundleActivator {
    private static final String CONFIG_PID = "netconf";
    private ServiceRegistration configService;

    @Override
    public void start(BundleContext bundleContext) {
        configService = bundleContext.registerService(ManagedService.class,
                NetconfConfiguration.getInstance(), getNetconfConfigProperties());
    }

    @Override
    public void stop(BundleContext bundleContext) {
        if (configService != null) {
            configService.unregister();
            configService = null;
        }
    }

    private Hashtable<String, String> getNetconfConfigProperties() {
        Hashtable<String, String> properties = new Hashtable<>();
        properties.put(Constants.SERVICE_PID, CONFIG_PID);
        return properties;
    }
}
