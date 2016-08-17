/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.osgi;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import java.util.Hashtable;
import java.util.Optional;
import org.osgi.service.cm.ManagedService;

public class NetconfConfigurationActivator implements BundleActivator {
    private static final String CONFIG_PID = "netconf";
    private Optional<ServiceRegistration> configService;

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        configService = Optional.of(bundleContext.registerService(ManagedService.class.getName(),
                NetconfConfiguration.getInstance() , getNetconfConfigProperties()));
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        configService.ifPresent(e -> e.unregister());
    }

    private Hashtable<String, String> getNetconfConfigProperties(){
        Hashtable<String, String> properties = new Hashtable<String, String>();
        properties.put(Constants.SERVICE_PID, CONFIG_PID);
        return properties;
    }
}
