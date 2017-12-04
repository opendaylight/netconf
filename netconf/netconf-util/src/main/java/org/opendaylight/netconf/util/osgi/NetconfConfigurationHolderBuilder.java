/*
 * Copyright (c) 2017 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util.osgi;

import java.net.InetSocketAddress;
import org.opendaylight.yangtools.concepts.Builder;

public class NetconfConfigurationHolderBuilder implements Builder<NetconfConfigurationHolder> {
    private InetSocketAddress tcpServerAddress;
    private InetSocketAddress sshServerAddress;
    private String privateKeyPath;
    private String keyStoreFile;
    private String keyStorePassword;
    private String trustStoreFile;
    private String trustStorePassword;

    private NetconfConfigurationHolderBuilder() {
    }

    public static NetconfConfigurationHolderBuilder create() {
        return new NetconfConfigurationHolderBuilder();
    }

    public InetSocketAddress getTcpServerAddress() {
        return tcpServerAddress;
    }

    public InetSocketAddress getSshServerAddress() {
        return sshServerAddress;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public String getKeyStoreFile() {
        return keyStoreFile;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public String getTrustStoreFile() {
        return trustStoreFile;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    @Override
    public NetconfConfigurationHolder build() {
        return new NetconfConfigurationHolder(this);
    }

    public NetconfConfigurationHolderBuilder withTcpServerAddress(InetSocketAddress value) {
        this.tcpServerAddress = value;
        return this;
    }

    public NetconfConfigurationHolderBuilder withSshServerAddress(InetSocketAddress value) {
        this.sshServerAddress = value;
        return this;
    }

    public NetconfConfigurationHolderBuilder withPrivateKeyPath(String value) {
        this.privateKeyPath = value;
        return this;
    }

    public NetconfConfigurationHolderBuilder withKeyStoreFile(String value) {
        this.keyStoreFile = value;
        return this;
    }

    public NetconfConfigurationHolderBuilder withKeyStorePassword(String value) {
        this.keyStorePassword = value;
        return this;
    }

    public NetconfConfigurationHolderBuilder withTrustStoreFile(String value) {
        this.trustStoreFile = value;
        return this;
    }

    public NetconfConfigurationHolderBuilder withTrustStorePassword(String value) {
        this.trustStorePassword = value;
        return this;
    }
}
