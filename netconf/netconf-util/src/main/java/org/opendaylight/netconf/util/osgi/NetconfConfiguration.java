/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.osgi;

import io.netty.channel.local.LocalAddress;
import java.net.InetSocketAddress;
import java.util.Dictionary;
import java.util.concurrent.TimeUnit;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfConfiguration implements ManagedService {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfConfiguration.class);

    private static final NetconfConfiguration INSTANCE = new NetconfConfiguration();
    private NetconfConfigurationHolder netconfConfiguration;

    /**
     * Props to access information within the dictionary.
     */

    private static final String SSH_ADDRESS_PROP = "ssh-address";
    private static final String SSH_PORT_PROP = "ssh-port";
    private static final String TCP_ADDRESS_PROP = "tcp-address";
    private static final String TCP_PORT_PROP = "tcp-port";
    private static final String SSH_PK_PATH_PROP = "ssh-pk-path";

    /**
     * Default values used if no dictionary is provided.
     */

    private static final String LOCAL_HOST = "127.0.0.1";
    private static final String INADDR_ANY = "0.0.0.0";
    private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final String DEFAULT_PRIVATE_KEY_PATH = "./configuration/RSA.pk";
    private static final InetSocketAddress DEFAULT_TCP_SERVER_ADRESS = new InetSocketAddress(LOCAL_HOST, 8383);
    private static final InetSocketAddress DEFAULT_SSH_SERVER_ADRESS = new InetSocketAddress(INADDR_ANY, 1830);
    private static final LocalAddress NETCONF_LOCAL_ADDRESS = new LocalAddress("netconf");

    public static NetconfConfiguration getInstance() {
        return INSTANCE;
    }

    private NetconfConfiguration() {
        netconfConfiguration = new NetconfConfigurationHolder(DEFAULT_TCP_SERVER_ADRESS,
                DEFAULT_SSH_SERVER_ADRESS, DEFAULT_PRIVATE_KEY_PATH);
    }

    @Override
    public void updated(final Dictionary<String, ?> dictionaryConfig) {
        if (dictionaryConfig == null) {
            LOG.trace("CSS netconf server configuration cannot be updated as passed dictionary is null");
            return;
        }
        final InetSocketAddress sshServerAddress = new InetSocketAddress((String) dictionaryConfig.get(SSH_ADDRESS_PROP),
                Integer.parseInt((String) dictionaryConfig.get(SSH_PORT_PROP)));
        final InetSocketAddress tcpServerAddress = new InetSocketAddress((String) dictionaryConfig.get(TCP_ADDRESS_PROP),
                Integer.parseInt((String) dictionaryConfig.get(TCP_PORT_PROP)));

        netconfConfiguration = new NetconfConfigurationHolder(tcpServerAddress,
                sshServerAddress,
                (String) dictionaryConfig.get(SSH_PK_PATH_PROP));

        LOG.debug("CSS netconf server configuration was updated: {}", dictionaryConfig.toString());
    }

    public InetSocketAddress getSshServerAddress(){
        return netconfConfiguration.getSshServerAddress();
    }

    public InetSocketAddress getTcpServerAddress(){
        return netconfConfiguration.getTcpServerAddress();
    }

    public String getPrivateKeyPath() {
        return netconfConfiguration.getPrivateKeyPath();
    }

    public static long getConnectionTimeoutMillis() {
        return DEFAULT_TIMEOUT_MILLIS;
    }

    public static LocalAddress getNetconfLocalAddress() {
        return NETCONF_LOCAL_ADDRESS;
    }
}