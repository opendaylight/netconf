/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.osgi;

import java.net.InetSocketAddress;
import java.util.Dictionary;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfConfiguration implements ManagedService {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfConfiguration.class);

    private static final NetconfConfiguration instance = new NetconfConfiguration();
    private NetconfConfigurationHolder netconfConfiguration;

    public static final String KEY_SSH_ADDRESS = "ssh-address";
    public static final String KEY_SSH_PORT = "ssh-port";
    public static final String KEY_TCP_ADDRESS = "tcp-address";
    public static final String KEY_TCP_PORT = "tcp-port";
    public static final String KEY_SSH_PK_PATH = "ssh-pk-path";
    public static final String KEY_SSH_AUTHORIZED_KEYS = "ssh-authorized-keys";

    public static NetconfConfiguration getInstance() {
        return instance;
    }

    private NetconfConfiguration() {
        netconfConfiguration = new NetconfConfigurationHolder(NetconfConfigUtil.DEFAULT_TCP_SERVER_ADRESS,
                NetconfConfigUtil.DEFAULT_SSH_SERVER_ADRESS, NetconfConfigUtil.DEFAULT_PRIVATE_KEY_PATH, "");
    }

    @Override
    public void updated(final Dictionary<String, ?> dictionaryConfig) {
        if (dictionaryConfig == null) {
            LOG.warn("Netconf configuration cannot be updated.");
            return;
        }
        final InetSocketAddress sshServerAddress = new InetSocketAddress((String) dictionaryConfig.get(KEY_SSH_ADDRESS),
                Integer.parseInt((String) dictionaryConfig.get(KEY_SSH_PORT)));
        final InetSocketAddress tcpServerAddress = new InetSocketAddress((String) dictionaryConfig.get(KEY_TCP_ADDRESS),
                Integer.parseInt((String) dictionaryConfig.get(KEY_TCP_PORT)));

        final String authorizedKeysPath = (String) dictionaryConfig.get(KEY_SSH_AUTHORIZED_KEYS);
        netconfConfiguration = new NetconfConfigurationHolder(tcpServerAddress, sshServerAddress,
                (String) dictionaryConfig.get(KEY_SSH_PK_PATH), authorizedKeysPath);

        LOG.info("Netconf configuration was updated: {}", dictionaryConfig.toString());
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

    public String getAuthorizedKeysPath() {
        return netconfConfiguration.getAuthorizedKeysPath();
    }
}