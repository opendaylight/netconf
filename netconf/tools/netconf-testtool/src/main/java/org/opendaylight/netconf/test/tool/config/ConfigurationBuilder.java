/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.config;

import java.io.File;
import java.util.Set;

import org.apache.sshd.server.PublickeyAuthenticator;
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.netconf.test.tool.TesttoolParameters;
import org.opendaylight.netconf.test.tool.operations.OperationsCreator;
import org.opendaylight.netconf.test.tool.rpchandler.RpcHandler;

public class ConfigurationBuilder {

    private Configuration configuration;

    public ConfigurationBuilder() {
        this.configuration = new Configuration();
    }

    public ConfigurationBuilder setPublickeyAuthenticator(PublickeyAuthenticator publickeyAuthenticator) {
        this.configuration.setPublickeyAuthenticator(publickeyAuthenticator);
        return this;
    }

    public ConfigurationBuilder setAuthProvider(AuthProvider authProvider) {
        this.configuration.setAuthProvider(authProvider);
        return this;
    }

    public ConfigurationBuilder setGetDefaultYangResources(Set<YangResource> defaultYangResources) {
        this.configuration.setDefaultYangResources(defaultYangResources);
        return this;
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

    public ConfigurationBuilder setRpcMapping(RpcHandler rpcHandler) {
        this.configuration.setRpcHandler(rpcHandler);
        return this;
    }

    @Deprecated
    public ConfigurationBuilder setMdSal(boolean mdSal) {
        this.configuration.setMdSal(mdSal);
        return this;
    }

    @Deprecated
    public ConfigurationBuilder setRpcConfigFile(File rpcConfigFile) {
        this.configuration.setRpcConfigFile(rpcConfigFile);
        return this;
    }

    @Deprecated
    public ConfigurationBuilder setInitialConfigXMLFile(File initialConfigXMLFile) {
        this.configuration.setInitialConfigXMLFile(initialConfigXMLFile);
        return this;
    }

    @Deprecated
    public ConfigurationBuilder setNotificationFile(File notificationFile) {
        this.configuration.setNotificationFile(notificationFile);
        return this;
    }

    public ConfigurationBuilder setOperationsCreator(OperationsCreator operationsCreator) {
        this.configuration.setOperationsCreator(operationsCreator);
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
        this.configuration.setRpcHandler(configuration.getRpcHandler());
        this.configuration.setOperationsCreator(configuration.getOperationsCreator());
        this.configuration.setMdSal(configuration.isMdSal());
        this.configuration.setRpcConfigFile(configuration.getRpcConfigFile());
        this.configuration.setInitialConfigXMLFile(configuration.getInitialConfigXMLFile());
        this.configuration.setNotificationFile(configuration.getNotificationFile());
        this.configuration.setSchemasDir(configuration.getSchemasDir());
        this.configuration.setDefaultYangResources(configuration.getDefaultYangResources());
        this.configuration.setAuthProvider(configuration.getAuthProvider());
        this.configuration.setPublickeyAuthenticator(configuration.getPublickeyAuthenticator());
        return this;
    }

    public ConfigurationBuilder from(TesttoolParameters testtoolParameters) {
        this.configuration.setGenerateConfigsTimeout(testtoolParameters.generateConfigsTimeout);
        this.configuration.setStartingPort(testtoolParameters.startingPort);
        this.configuration.setDeviceCount(testtoolParameters.deviceCount);
        this.configuration.setSsh(testtoolParameters.ssh);
        this.configuration.setIp(testtoolParameters.ip);
        this.configuration.setMdSal(testtoolParameters.mdSal);
        this.configuration.setRpcConfigFile(testtoolParameters.rpcConfig);
        this.configuration.setInitialConfigXMLFile(testtoolParameters.initialConfigXMLFile);
        this.configuration.setNotificationFile(testtoolParameters.notificationFile);
        this.configuration.setSchemasDir(testtoolParameters.schemasDir);
        return this;
    }

    public Configuration build() {
        return configuration;
    }

}
