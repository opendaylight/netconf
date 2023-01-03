/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util;

import java.net.InetSocketAddress;

final class NetconfConfigurationHolder {

    private final InetSocketAddress tcpServerAddress;
    private final InetSocketAddress sshServerAddress;
    private final String privateKeyPath;

    NetconfConfigurationHolder(final InetSocketAddress tcpServerAddress,
                               final InetSocketAddress sshServerAddress,
                               final String privateKeyPath) {
        this.tcpServerAddress = tcpServerAddress;
        this.sshServerAddress = sshServerAddress;
        this.privateKeyPath = privateKeyPath;
    }

    String getPrivateKeyPath() {
        return privateKeyPath;
    }

    InetSocketAddress getSshServerAddress() {
        return sshServerAddress;
    }

    InetSocketAddress getTcpServerAddress() {
        return tcpServerAddress;
    }
}
