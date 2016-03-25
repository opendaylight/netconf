/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.impl;

import org.opendaylight.controller.config.persist.api.ConfigPusher;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.netconf.console.api.NetconfConsoleProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfProvider implements BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConsoleProviderImpl.class);
    private ServiceRegistration<NetconfConsoleProvider> netconfConsoleRegistration;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("NetconfProvider Session Initiated");

        // Retrieve DataBroker service to interact with md-sal
        final DataBroker dataBroker =  session.getSALService(DataBroker.class);

        // Initialization of NETCONF Console Provider service implementation
        initializeNetconfConsoleProvider(dataBroker);
    }

    private void initializeNetconfConsoleProvider(DataBroker dataBroker) {
        final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();

        // Retrieve ConfigPusher instance from OSGi services
        final ServiceReference<?> serviceReference = context.getServiceReference(ConfigPusher.class.getName());
        final ConfigPusher configPusher = (ConfigPusher) context.getService(serviceReference);

        // Initialize NetconfConsoleProviderImpl class
        final NetconfConsoleProviderImpl consoleProvider = new NetconfConsoleProviderImpl(dataBroker, configPusher);

        // Register the NetconfConsoleProvider service
        netconfConsoleRegistration = context.registerService(NetconfConsoleProvider.class, consoleProvider, null);
    }

    @Override
    public void close() throws Exception {
        LOG.info("NetconfProvider closed.");
        netconfConsoleRegistration.unregister();
    }
}
