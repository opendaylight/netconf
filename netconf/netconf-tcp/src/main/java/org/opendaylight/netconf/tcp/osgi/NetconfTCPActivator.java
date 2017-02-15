/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.tcp.osgi;

import java.net.InetSocketAddress;
import org.opendaylight.netconf.tcp.netty.ProxyServer;
import org.opendaylight.netconf.util.osgi.NetconfConfigUtil;
import org.opendaylight.netconf.util.osgi.NetconfConfiguration;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens TCP port specified in config.ini, creates bridge between this port and local netconf server.
 */
public class NetconfTCPActivator implements BundleActivator {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTCPActivator.class);
    private ProxyServer proxyServer;

    @Override
    public void start(BundleContext context) throws InvalidSyntaxException {
        final NetconfConfiguration netconfConfiguration = NetconfConfigUtil.getNetconfConfigurationService(context);

        final InetSocketAddress address = netconfConfiguration.getTcpServerAddress();

        if (address.getAddress().isAnyLocalAddress()) {
            LOG.warn("Unprotected netconf TCP address is configured to ANY local address. This is a security risk. " +
                            "Consider changing tcp-address in netconf.cfg to 127.0.0.1");
        }
        LOG.info("Starting TCP netconf server at {}", address);
        proxyServer = new ProxyServer(address, NetconfConfiguration.NETCONF_LOCAL_ADDRESS);
    }

    @Override
    public void stop(BundleContext context) {
        if (proxyServer != null) {
            proxyServer.close();
        }
    }
}
