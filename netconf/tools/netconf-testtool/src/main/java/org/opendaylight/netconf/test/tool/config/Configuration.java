/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.config;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.netconf.shaded.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.opendaylight.netconf.test.tool.operations.OperationsCreator;
import org.opendaylight.netconf.test.tool.rpchandler.RpcHandler;
import org.opendaylight.netconf.test.tool.rpchandler.RpcHandlerDefault;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {
    private static final Logger LOG = LoggerFactory.getLogger(Configuration.class);

    public static final Set<String> DEFAULT_BASE_CAPABILITIES_EXI = ImmutableSet.of(
        CapabilityURN.BASE,
        CapabilityURN.BASE_1_1,
        CapabilityURN.EXI,
        CapabilityURN.NOTIFICATION);

    public static final Set<String> DEFAULT_BASE_CAPABILITIES = ImmutableSet.of(
        CapabilityURN.BASE,
        CapabilityURN.BASE_1_1);

    public static final Set<YangResource> DEFAULT_YANG_RESOURCES = ImmutableSet.of(
            new YangResource("ietf-netconf-monitoring", "2010-10-04",
                    "/META-INF/yang/ietf-netconf-monitoring@2010-10-04.yang"),
            new YangResource("odl-netconf-monitoring", "2022-07-18",
                    "/META-INF/yang/odl-netconf-monitoring@2022-07-18.yang"),
            new YangResource("ietf-yang-types", "2013-07-15",
                    "/META-INF/yang/ietf-yang-types@2013-07-15.yang"),
            new YangResource("ietf-inet-types", "2013-07-15",
                    "/META-INF/yang/ietf-inet-types@2013-07-15.yang")
    );

    public static final AuthProvider DEFAULT_AUTH_PROVIDER = (username, password) -> {
        LOG.info("Auth with username and password: {}", username);
        return true;
    };

    public static final PublickeyAuthenticator DEFAULT_PUBLIC_KEY_AUTHENTICATOR = (username, key, session) -> {
        LOG.info("Auth with public key: {}", key);
        return true;
    };

    private int generateConfigsTimeout = (int) TimeUnit.MINUTES.toMillis(30);
    private int threadPoolSize = 8;
    private int startingPort = 17830;
    private int deviceCount = 1;
    private boolean ssh = true;
    private String ip = "0.0.0.0";
    private Set<YangResource> defaultYangResources = DEFAULT_YANG_RESOURCES;

    private Set<YangModuleInfo> models;
    private Set<String> capabilities = DEFAULT_BASE_CAPABILITIES_EXI;
    private RpcHandler rpcHandler = new RpcHandlerDefault();
    private OperationsCreator operationsCreator;
    private AuthProvider authProvider = DEFAULT_AUTH_PROVIDER;
    private PublickeyAuthenticator publickeyAuthenticator = DEFAULT_PUBLIC_KEY_AUTHENTICATOR;

    @Deprecated
    private boolean mdSal = false;

    @Deprecated
    private File rpcConfigFile;

    @Deprecated
    private File notificationFile;

    @Deprecated
    private File initialConfigXMLFile;

    @Deprecated
    private File schemasDir;

    public Configuration() {
    }

    public PublickeyAuthenticator getPublickeyAuthenticator() {
        return publickeyAuthenticator;
    }

    public void setPublickeyAuthenticator(final PublickeyAuthenticator publickeyAuthenticator) {
        this.publickeyAuthenticator = publickeyAuthenticator;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(final AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public Set<YangResource> getDefaultYangResources() {
        return defaultYangResources;
    }

    public void setDefaultYangResources(final Set<YangResource> defaultYangResources) {
        this.defaultYangResources = defaultYangResources;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(final int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public int getStartingPort() {
        return startingPort;
    }

    public void setStartingPort(final int startingPort) {
        this.startingPort = startingPort;
    }

    public int getDeviceCount() {
        return deviceCount;
    }

    public void setDeviceCount(final int deviceCount) {
        this.deviceCount = deviceCount;
    }

    public int getGenerateConfigsTimeout() {
        return generateConfigsTimeout;
    }

    public void setGenerateConfigsTimeout(final int generateConfigsTimeout) {
        this.generateConfigsTimeout = generateConfigsTimeout;
    }

    public boolean isSsh() {
        return ssh;
    }

    public void setSsh(final boolean ssh) {
        this.ssh = ssh;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(final String ip) {
        this.ip = ip;
    }

    public Set<YangModuleInfo> getModels() {
        return models;
    }

    public void setModels(final Set<YangModuleInfo> models) {
        this.models = models;
    }

    public Set<String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(final Set<String> capabilities) {
        this.capabilities = capabilities;
    }

    public RpcHandler getRpcHandler() {
        return rpcHandler;
    }

    public void setRpcHandler(final RpcHandler rpcHandler) {
        this.rpcHandler = rpcHandler;
    }

    public OperationsCreator getOperationsCreator() {
        return operationsCreator;
    }

    public void setOperationsCreator(final OperationsCreator operationsCreator) {
        this.operationsCreator = operationsCreator;
    }

    @Deprecated
    public boolean isMdSal() {
        return mdSal;
    }

    @Deprecated
    public void setMdSal(final boolean mdSal) {
        this.mdSal = mdSal;
    }

    @Deprecated
    public File getRpcConfigFile() {
        return rpcConfigFile;
    }

    @Deprecated
    public void setRpcConfigFile(final File rpcConfigFile) {
        this.rpcConfigFile = rpcConfigFile;
    }

    @Deprecated
    public File getNotificationFile() {
        return notificationFile;
    }

    @Deprecated
    public void setNotificationFile(final File notificationFile) {
        this.notificationFile = notificationFile;
    }

    @Deprecated
    public File getInitialConfigXMLFile() {
        return initialConfigXMLFile;
    }

    @Deprecated
    public void setInitialConfigXMLFile(final File initialConfigXMLFile) {
        this.initialConfigXMLFile = initialConfigXMLFile;
    }

    @Deprecated
    public boolean isXmlConfigurationProvided() {
        return initialConfigXMLFile != null;
    }

    @Deprecated
    public boolean isNotificationsSupported() {
        return notificationFile != null;
    }

    @Deprecated
    public File getSchemasDir() {
        return schemasDir;
    }

    @Deprecated
    public void setSchemasDir(final File schemasDir) {
        this.schemasDir = schemasDir;
    }
}
