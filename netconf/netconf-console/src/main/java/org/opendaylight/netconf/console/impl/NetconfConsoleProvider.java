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
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfConsoleProvider implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConsoleProvider.class);
    private ServiceRegistration<NetconfCommands> netconfConsoleRegistration;

    public NetconfConsoleProvider(DataBroker dataBroker, BundleContext context, MountPointService mountService) {
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
