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
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.netconf.shaded.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.opendaylight.netconf.test.tool.TesttoolParameters;
import org.opendaylight.netconf.test.tool.operations.OperationsCreator;
import org.opendaylight.netconf.test.tool.rpchandler.RpcHandler;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;

public class ConfigurationBuilder {

    private final Configuration configuration;

    public ConfigurationBuilder() {
        this.configuration = new Configuration();
    }

    public ConfigurationBuilder setPublickeyAuthenticator(final PublickeyAuthenticator publickeyAuthenticator) {
        this.configuration.setPublickeyAuthenticator(publickeyAuthenticator);
        return this;
    }

    public ConfigurationBuilder setAuthProvider(final AuthProvider authProvider) {
        this.configuration.setAuthProvider(authProvider);
        return this;
    }

    public ConfigurationBuilder setGetDefaultYangResources(final Set<YangResource> defaultYangResources) {
        this.configuration.setDefaultYangResources(defaultYangResources);
        return this;
    }

    public ConfigurationBuilder setThreadPoolSize(final int threadPoolSize) {
        this.configuration.setThreadPoolSize(threadPoolSize);
        return this;
    }

    public ConfigurationBuilder setGenerateConfigsTimeout(final int generateConfigsTimeout) {
        this.configuration.setGenerateConfigsTimeout(generateConfigsTimeout);
        return this;
    }

    public ConfigurationBuilder setModels(final Set<YangModuleInfo> models) {
        this.configuration.setModels(models);
        return this;
    }

    public ConfigurationBuilder setCapabilities(final Set<String> capabilities) {
        this.configuration.setCapabilities(capabilities);
        return this;
    }

    public ConfigurationBuilder setStartingPort(final int startingPort) {
        this.configuration.setStartingPort(startingPort);
        return this;
    }

    public ConfigurationBuilder setDeviceCount(final int deviceCount) {
        this.configuration.setDeviceCount(deviceCount);
        return this;
    }

    public ConfigurationBuilder setSsh(final boolean ssh) {
        this.configuration.setSsh(ssh);
        return this;
    }

    public ConfigurationBuilder setIp(final String ip) {
        this.configuration.setIp(ip);
        return this;
    }

    public ConfigurationBuilder setRpcMapping(final RpcHandler rpcHandler) {
        this.configuration.setRpcHandler(rpcHandler);
        return this;
    }

    @Deprecated
    public ConfigurationBuilder setMdSal(final boolean mdSal) {
        this.configuration.setMdSal(mdSal);
        return this;
    }

    @Deprecated
    public ConfigurationBuilder setRpcConfigFile(final File rpcConfigFile) {
        this.configuration.setRpcConfigFile(rpcConfigFile);
        return this;
    }

    @Deprecated
    public ConfigurationBuilder setInitialConfigXMLFile(final File initialConfigXMLFile) {
        this.configuration.setInitialConfigXMLFile(initialConfigXMLFile);
        return this;
    }

    @Deprecated
    public ConfigurationBuilder setNotificationFile(final File notificationFile) {
        this.configuration.setNotificationFile(notificationFile);
        return this;
    }

    public ConfigurationBuilder setOperationsCreator(final OperationsCreator operationsCreator) {
        this.configuration.setOperationsCreator(operationsCreator);
        return this;
    }

    public ConfigurationBuilder from(final Configuration template) {
        this.configuration.setThreadPoolSize(template.getThreadPoolSize());
        this.configuration.setGenerateConfigsTimeout(template.getGenerateConfigsTimeout());
        this.configuration.setModels(template.getModels());
        this.configuration.setCapabilities(template.getCapabilities());
        this.configuration.setStartingPort(template.getStartingPort());
        this.configuration.setDeviceCount(template.getDeviceCount());
        this.configuration.setSsh(template.isSsh());
        this.configuration.setIp(template.getIp());
        this.configuration.setRpcHandler(template.getRpcHandler());
        this.configuration.setOperationsCreator(template.getOperationsCreator());
        this.configuration.setMdSal(template.isMdSal());
        this.configuration.setRpcConfigFile(template.getRpcConfigFile());
        this.configuration.setInitialConfigXMLFile(template.getInitialConfigXMLFile());
        this.configuration.setNotificationFile(template.getNotificationFile());
        this.configuration.setSchemasDir(template.getSchemasDir());
        this.configuration.setDefaultYangResources(template.getDefaultYangResources());
        this.configuration.setAuthProvider(template.getAuthProvider());
        this.configuration.setPublickeyAuthenticator(template.getPublickeyAuthenticator());
        return this;
    }

    public ConfigurationBuilder from(final TesttoolParameters testtoolParameters) {
        this.configuration.setGenerateConfigsTimeout(testtoolParameters.getGenerateConfigsTimeout());
        this.configuration.setStartingPort(testtoolParameters.getStartingPort());
        this.configuration.setDeviceCount(testtoolParameters.getDeviceCount());
        this.configuration.setSsh(testtoolParameters.isSsh());
        this.configuration.setIp(testtoolParameters.getIp());
        this.configuration.setMdSal(testtoolParameters.isMdSal());
        this.configuration.setRpcConfigFile(testtoolParameters.getRpcConfig());
        this.configuration.setInitialConfigXMLFile(testtoolParameters.getInitialConfigXMLFile());
        this.configuration.setNotificationFile(testtoolParameters.getNotificationFile());
        this.configuration.setSchemasDir(testtoolParameters.getSchemasDir());
        this.configuration.setCapabilities(testtoolParameters.isExi() ? Configuration.DEFAULT_BASE_CAPABILITIES_EXI
                : Configuration.DEFAULT_BASE_CAPABILITIES);
        return this;
    }

    public Configuration build() {
        return configuration;
    }

}
