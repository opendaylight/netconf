/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl.conf;

import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for setting config property from xml.
 */
public class NetconfTopologyImplConfig {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyImplConfig.class);

    private final NetconfTopologyImpl netconfTopology;

    public NetconfTopologyImplConfig(NetconfTopologyImpl netconfTopology) {
        this.netconfTopology = netconfTopology;
    }

    public void setPrivateKeyPath(final String privateKeyPath) {
        netconfTopology.setPrivateKeyPath(privateKeyPath);
    }

    public void setPrivateKeyPassphrase(final String privateKeyPassphrase) {
        netconfTopology.setPrivateKeyPassphrase(privateKeyPassphrase);
    }

}
