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
    private long connectionTimeoutMillis;

    NetconfConfigurationHolder(final NetconfConfigurationHolderBuilder builder) {
        this.tcpServerAddress = builder.getTcpServerAddress();
        this.sshServerAddress = builder.getSshServerAddress();
        this.privateKeyPath = builder.getPrivateKeyPath();
        this.connectionTimeoutMillis = builder.getConnectionTimeoutMillis();
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

    long getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    public static class NetconfConfigurationHolderBuilder {

        private InetSocketAddress tcpServerAddress;
        private InetSocketAddress sshServerAddress;
        private String privateKeyPath;
        private long connectionTimeoutMillis;

        private NetconfConfigurationHolderBuilder() {
        }

        public NetconfConfigurationHolder build() {
            return new NetconfConfigurationHolder(this);
        }

        private String getPrivateKeyPath() {
            return privateKeyPath;
        }

        private InetSocketAddress getSshServerAddress() {
            return sshServerAddress;
        }

        private InetSocketAddress getTcpServerAddress() {
            return tcpServerAddress;
        }

        private long getConnectionTimeoutMillis() {
            return connectionTimeoutMillis;
        }

        public NetconfConfigurationHolderBuilder setConnectionTimeoutMillis(long connectionTimeoutMillis) {
            this.connectionTimeoutMillis = connectionTimeoutMillis;
            return this;
        }

        NetconfConfigurationHolderBuilder setPrivateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
            return this;
        }

        NetconfConfigurationHolderBuilder setSshServerAddress(InetSocketAddress sshServerAddress) {
            this.sshServerAddress = sshServerAddress;
            return this;
        }

        NetconfConfigurationHolderBuilder setTcpServerAddress(InetSocketAddress tcpServerAddress) {
            this.tcpServerAddress = tcpServerAddress;
            return this;
        }

        static NetconfConfigurationHolderBuilder create() {
            return new NetconfConfigurationHolderBuilder();
        }
    }
}
