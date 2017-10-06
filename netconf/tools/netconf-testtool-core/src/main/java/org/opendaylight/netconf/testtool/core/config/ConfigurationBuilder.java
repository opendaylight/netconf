/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.testtool.core.config;

import org.opendaylight.netconf.testtool.core.impl.rpc.RpcMapping;

import java.util.Set;

public class ConfigurationBuilder {

    private Configuration configuration;

    public ConfigurationBuilder() {
        this.configuration = new Configuration();
    }

    public ConfigurationBuilder setThreadPoolSize(int threadPoolSize) {
        this.configuration.setThreadPoolSize(threadPoolSize);
        return this;
    }

    public ConfigurationBuilder setGenerateConfigsTimeout(int generateConfigsTimeout) {
        this.configuration.setGenerateConfigsTimeout(generateConfigsTimeout);
        return this;
    }

    public ConfigurationBuilder setModels(Set<String> models) {
        this.configuration.setModels(models);
        return this;
    }

    public ConfigurationBuilder setCapabilities(Set<String> capabilities) {
        this.configuration.setCapabilities(capabilities);
        return this;
    }

    public ConfigurationBuilder setStartingPort(int startingPort) {
        this.configuration.setStartingPort(startingPort);
        return this;
    }

    public ConfigurationBuilder setDeviceCount(int deviceCount) {
        this.configuration.setDeviceCount(deviceCount);
        return this;
    }

    public ConfigurationBuilder setSsh(boolean ssh) {
        this.configuration.setSsh(ssh);
        return this;
    }

    public ConfigurationBuilder setIp(String ip) {
        this.configuration.setIp(ip);
        return this;
    }

    public ConfigurationBuilder setRpcMapping(RpcMapping rpcMapping) {
        this.configuration.setRpcMapping(rpcMapping);
        return this;
    }

    public ConfigurationBuilder from(Configuration configuration) {
        this.configuration.setThreadPoolSize(configuration.getThreadPoolSize());
        this.configuration.setGenerateConfigsTimeout(configuration.getGenerateConfigsTimeout());
        this.configuration.setModels(configuration.getModels());
        this.configuration.setCapabilities(configuration.getCapabilities());
        this.configuration.setStartingPort(configuration.getStartingPort());
        this.configuration.setDeviceCount(configuration.getDeviceCount());
        this.configuration.setSsh(configuration.isSsh());
        this.configuration.setIp(configuration.getIp());
        this.configuration.setRpcMapping(configuration.getRpcMapping());
        return this;
    }

    public Configuration build() {
        return configuration;
    }

}
