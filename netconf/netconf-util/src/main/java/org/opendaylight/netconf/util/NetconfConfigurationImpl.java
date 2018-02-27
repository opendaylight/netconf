/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util;

import java.net.InetSocketAddress;
import java.util.Dictionary;
import java.util.Hashtable;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfConfigurationImpl implements NetconfConfiguration, ManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConfigurationImpl.class);

    /*
     * Props to access information within the dictionary.
     */

    private static final String SSH_ADDRESS_PROP = "ssh-address";
    private static final String SSH_PORT_PROP = "ssh-port";
    private static final String TCP_ADDRESS_PROP = "tcp-address";
    private static final String TCP_PORT_PROP = "tcp-port";
    private static final String SSH_PK_PATH_PROP = "ssh-pk-path";

    private NetconfConfigurationHolder netconfConfiguration;

    public NetconfConfigurationImpl(final String tcpServerAddress, final String tcpServerPort,
                                    final String sshServerAddress, final String sshServerPort,
                                    final String privateKeyPath) throws NumberFormatException {

        // isolate configuration to "updated(...)" instead of repeating logic here
        final Dictionary<String, String> dictionaryConfig = new Hashtable<>();
        dictionaryConfig.put(TCP_ADDRESS_PROP, tcpServerAddress);
        dictionaryConfig.put(TCP_PORT_PROP, tcpServerPort);
        dictionaryConfig.put(SSH_ADDRESS_PROP, sshServerAddress);
        dictionaryConfig.put(SSH_PORT_PROP, sshServerPort);
        dictionaryConfig.put(SSH_PK_PATH_PROP, privateKeyPath);

        updated(dictionaryConfig);
    }

    @Override
    public void updated(final Dictionary<String, ?> dictionaryConfig) {
        if (dictionaryConfig == null) {
            LOG.debug("CSS NETCONF server configuration cannot be updated as passed dictionary is null");
            return;
        }
        final InetSocketAddress sshServerAddress =
                new InetSocketAddress((String) dictionaryConfig.get(SSH_ADDRESS_PROP),
                        Integer.parseInt((String) dictionaryConfig.get(SSH_PORT_PROP)));
        final InetSocketAddress tcpServerAddress =
                new InetSocketAddress((String) dictionaryConfig.get(TCP_ADDRESS_PROP),
                Integer.parseInt((String) dictionaryConfig.get(TCP_PORT_PROP)));

        netconfConfiguration = new NetconfConfigurationHolder(tcpServerAddress,
                sshServerAddress,
                (String) dictionaryConfig.get(SSH_PK_PATH_PROP));

        LOG.debug("CSS netconf server configuration was updated: {}", dictionaryConfig.toString());
    }

    @Override
    public InetSocketAddress getSshServerAddress() {
        return netconfConfiguration.getSshServerAddress();
    }

    @Override
    public InetSocketAddress getTcpServerAddress() {
        return netconfConfiguration.getTcpServerAddress();
    }

    @Override
    public String getPrivateKeyPath() {
        return netconfConfiguration.getPrivateKeyPath();
    }
}