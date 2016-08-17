/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.osgi;

import java.net.InetSocketAddress;

final class NetconfConfigurationHolder {

    private InetSocketAddress tcpServerAddress;
    private InetSocketAddress sshServerAddress;
    private String privateKeyPath;

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public InetSocketAddress getSshServerAddress() {
        return sshServerAddress;
    }

    public void setSshServerAddress(InetSocketAddress sshServerAddress) {
        this.sshServerAddress = sshServerAddress;
    }

    public InetSocketAddress getTcpServerAddress() {
        return tcpServerAddress;
    }

    public void setTcpServerAddress(InetSocketAddress tcpServerAddress) {
        this.tcpServerAddress = tcpServerAddress;
    }

}
