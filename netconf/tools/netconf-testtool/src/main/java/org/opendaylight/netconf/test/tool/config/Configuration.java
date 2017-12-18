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
import java.security.PublicKey;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.netconf.test.tool.operations.OperationsCreator;
import org.opendaylight.netconf.test.tool.rpchandler.RpcHandler;
import org.opendaylight.netconf.test.tool.rpchandler.RpcHandlerDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {

    private static final Logger LOG = LoggerFactory.getLogger(Configuration.class);

    public static final Set<String> DEFAULT_BASE_CAPABILITIES_EXI = ImmutableSet.of(
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1,
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_CAPABILITY_EXI_1_0
    );

    public static final Set<String> DEFAULT_BASE_CAPABILITIES = ImmutableSet.of(
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1
    );

    public static final Set<YangResource> DEFAULT_YANG_RESOURCES = ImmutableSet.of(
            new YangResource("ietf-netconf-monitoring", "2010-10-04",
                    "/META-INF/yang/ietf-netconf-monitoring.yang"),
            new YangResource("ietf-netconf-monitoring-extension", "2013-12-10",
                    "/META-INF/yang/ietf-netconf-monitoring-extension.yang"),
            new YangResource("ietf-yang-types", "2013-07-15",
                    "/META-INF/yang/ietf-yang-types@2013-07-15.yang"),
            new YangResource("ietf-inet-types", "2013-07-15",
                    "/META-INF/yang/ietf-inet-types@2013-07-15.yang")
    );

    public static final AuthProvider DEFAULT_AUTH_PROVIDER = new AuthProvider() {
        @Override
        public boolean authenticated(String username, String password) {
            LOG.info("Auth with username and password: {}", username);
            return true;
        }
    };

    public static final PublickeyAuthenticator DEFAULT_PUBLIC_KEY_AUTHENTICATOR = new PublickeyAuthenticator() {
        @Override
        public boolean authenticate(String username, PublicKey key, ServerSession session) {
            LOG.info("Auth with public key: {}", key);
            return true;
        }
    };

    private int generateConfigsTimeout = (int) TimeUnit.MINUTES.toMillis(30);
    private int threadPoolSize = 8;
    private int startingPort = 17830;
    private int deviceCount = 1;
    private boolean ssh = true;
    private String ip = "0.0.0.0";
    private Set<YangResource> defaultYangResources = DEFAULT_YANG_RESOURCES;

    private Set<String> models;
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

    public void setPublickeyAuthenticator(PublickeyAuthenticator publickeyAuthenticator) {
        this.publickeyAuthenticator = publickeyAuthenticator;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public Set<YangResource> getDefaultYangResources() {
        return defaultYangResources;
    }

    public void setDefaultYangResources(Set<YangResource> defaultYangResources) {
        this.defaultYangResources = defaultYangResources;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public int getStartingPort() {
        return startingPort;
    }

    public void setStartingPort(int startingPort) {
        this.startingPort = startingPort;
    }

    public int getDeviceCount() {
        return deviceCount;
    }

    public void setDeviceCount(int deviceCount) {
        this.deviceCount = deviceCount;
    }

    public int getGenerateConfigsTimeout() {
        return generateConfigsTimeout;
    }

    public void setGenerateConfigsTimeout(int generateConfigsTimeout) {
        this.generateConfigsTimeout = generateConfigsTimeout;
    }

    public boolean isSsh() {
        return ssh;
    }

    public void setSsh(boolean ssh) {
        this.ssh = ssh;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Set<String> getModels() {
        return models;
    }

    public void setModels(Set<String> models) {
        this.models = models;
    }

    public Set<String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Set<String> capabilities) {
        this.capabilities = capabilities;
    }

    public RpcHandler getRpcHandler() {
        return rpcHandler;
    }

    public void setRpcHandler(RpcHandler rpcHandler) {
        this.rpcHandler = rpcHandler;
    }

    public OperationsCreator getOperationsCreator() {
        return operationsCreator;
    }

    public void setOperationsCreator(OperationsCreator operationsCreator) {
        this.operationsCreator = operationsCreator;
    }

    @Deprecated
    public boolean isMdSal() {
        return mdSal;
    }

    @Deprecated
    public void setMdSal(boolean mdSal) {
        this.mdSal = mdSal;
    }

    @Deprecated
    public File getRpcConfigFile() {
        return rpcConfigFile;
    }

    @Deprecated
    public void setRpcConfigFile(File rpcConfigFile) {
        this.rpcConfigFile = rpcConfigFile;
    }

    @Deprecated
    public File getNotificationFile() {
        return notificationFile;
    }

    @Deprecated
    public void setNotificationFile(File notificationFile) {
        this.notificationFile = notificationFile;
    }

    @Deprecated
    public File getInitialConfigXMLFile() {
        return initialConfigXMLFile;
    }

    @Deprecated
    public void setInitialConfigXMLFile(File initialConfigXMLFile) {
        this.initialConfigXMLFile = initialConfigXMLFile;
    }

    @Deprecated
    public boolean isXmlConfigurationProvided() {
        return initialConfigXMLFile != null && notificationFile != null;
    }

    @Deprecated
    public File getSchemasDir() {
        return schemasDir;
    }

    @Deprecated
    public void setSchemasDir(File schemasDir) {
        this.schemasDir = schemasDir;
    }
}
