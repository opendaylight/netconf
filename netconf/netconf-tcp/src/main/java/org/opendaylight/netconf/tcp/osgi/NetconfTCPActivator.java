/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.tcp.osgi;

import com.google.common.base.Optional;
import java.net.InetSocketAddress;
import org.opendaylight.netconf.tcp.netty.ProxyServer;
import org.opendaylight.netconf.util.osgi.NetconfConfigUtil;
import org.opendaylight.netconf.util.osgi.NetconfConfigUtil.InfixProp;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens TCP port specified in config.ini, creates bridge between this port and local netconf server.
 */
public class NetconfTCPActivator {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTCPActivator.class);
    private ProxyServer proxyServer;

    private final BundleContext context;

    public NetconfTCPActivator(final BundleContext context) {
        this.context = context;
    }
    /**
     * Invoke by blueprint
     */
    public void start() {
        final Optional<InetSocketAddress> maybeAddress = NetconfConfigUtil.extractNetconfServerAddress(context, InfixProp.tcp);
        if (!maybeAddress.isPresent()) {
            LOG.warn("Netconf tcp server is not configured. Using default value {}",
                    NetconfConfigUtil.DEFAULT_TCP_SERVER_ADRESS);
        }

        InetSocketAddress address = maybeAddress.or(NetconfConfigUtil.DEFAULT_TCP_SERVER_ADRESS);

        if (address.getAddress().isAnyLocalAddress()) {
            LOG.warn("Unprotected netconf TCP address is configured to ANY local address. This is a security risk. Consider changing {} to 127.0.0.1",
                    NetconfConfigUtil.getNetconfServerAddressKey(InfixProp.tcp));
        }
        LOG.info("Starting TCP netconf server at {}", address);
        proxyServer = new ProxyServer(address, NetconfConfigUtil.getNetconfLocalAddress());
    }

    /**
     * Invoke by blueprint
     */
    public void stop() {
        if (proxyServer != null) {
            proxyServer.close();
        }
    }
}
