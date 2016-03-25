/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfConsoleProvider implements BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConsoleProvider.class);
    private ServiceRegistration<NetconfCommands> netconfConsoleRegistration;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("NetconfProvider Session Initiated");

        // Retrieve DataBroker service to interact with md-sal
        final DataBroker dataBroker =  session.getSALService(DataBroker.class);

        // Retrieve MountPointService to interact with NETCONF remote devices connected to ODL and register it
        final MountPointService mountService = session.getSALService(MountPointService.class);
        final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();

        // Initialization of NETCONF Console Provider service implementation
        initializeNetconfConsoleProvider(dataBroker, context, mountService);
    }

    private void initializeNetconfConsoleProvider(DataBroker dataBroker, BundleContext context, MountPointService mountService) {
        // Initialize NetconfConsoleProviderImpl class
        final NetconfCommandsImpl consoleProvider = new NetconfCommandsImpl(dataBroker, mountService);

        // Register the NetconfConsoleProvider service
        netconfConsoleRegistration = context.registerService(NetconfCommands.class, consoleProvider, null);
    }

    @Override
    public void close() throws Exception {
        LOG.info("NetconfProvider closed.");
        netconfConsoleRegistration.unregister();
    }
}
