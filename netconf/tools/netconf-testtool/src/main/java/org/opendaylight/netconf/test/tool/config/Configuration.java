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
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.test.tool.operations.OperationsCreator;
import org.opendaylight.netconf.test.tool.rpchandler.RpcHandler;
import org.opendaylight.netconf.test.tool.rpchandler.RpcHandlerDefault;


public class Configuration {

    public static final Set<String> DEFAULT_BASE_CAPABILITIES_EXI = ImmutableSet.of(
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1,
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_CAPABILITY_EXI_1_0
    );

    public static final Set<String> DEFAULT_BASE_CAPABILITIES = ImmutableSet.of(
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1
    );

    private int generateConfigsTimeout = (int) TimeUnit.MINUTES.toMillis(30);
    private int threadPoolSize = 8;
    private int startingPort = 17830;
    private int deviceCount = 1;
    private boolean ssh = true;
    private String ip = "0.0.0.0";

    private Set<String> models;
    private Set<String> capabilities = DEFAULT_BASE_CAPABILITIES_EXI;
    private RpcHandler rpcHandler = new RpcHandlerDefault();
    private OperationsCreator operationsCreator;

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
