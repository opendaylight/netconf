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
    private final InetSocketAddress tcpServerAddress;
    private final InetSocketAddress sshServerAddress;
    private final String privateKeyPath;
    private final String keyStoreFile;
    private final String keyStorePassword;
    private final String trustStoreFile;
    private final String trustStorePassword;

    NetconfConfigurationHolder(NetconfConfigurationHolderBuilder builder) {
        this.tcpServerAddress = builder.getTcpServerAddress();
        this.sshServerAddress = builder.getSshServerAddress();
        this.privateKeyPath = builder.getPrivateKeyPath();
        this.keyStoreFile = builder.getKeyStoreFile();
        this.keyStorePassword = builder.getKeyStorePassword();
        this.trustStoreFile = builder.getTrustStoreFile();
        this.trustStorePassword = builder.getTrustStorePassword();
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

    String getKeyStoreFile() {
        return keyStoreFile;
    }

    String getKeyStorePassword() {
        return keyStorePassword;
    }

    String getTrustStoreFile() {
        return trustStoreFile;
    }

    String getTrustStorePassword() {
        return trustStorePassword;
    }
}
