/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.testtool.core.config;

import com.google.common.collect.ImmutableSet;
import org.opendaylight.netconf.testtool.core.impl.rpc.RpcMapping;
import org.opendaylight.netconf.testtool.core.impl.rpc.RpcMappingDefaultImpl;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * This class replaces directly netconf test tool configuration
 * org.opendaylight.netconf.test.tool.TesttoolParameters
 */
public class Configuration {

    public static final Set<String> DEFAULT_BASE_CAPABILITIES = ImmutableSet.of(
            "urn:ietf:params:netconf:base:1.0",
            "urn:ietf:params:netconf:base:1.1",
            "urn:ietf:params:netconf:capability:exi:1.0"
    );

    private int generateConfigsTimeout = (int) TimeUnit.MINUTES.toMillis(30);
    private int threadPoolSize = 8;
    private int startingPort = 17830;
    private int deviceCount = 1;
    private boolean ssh = true;
    private String ip = "0.0.0.0";
    private Set<String> models;
    private Set<String> capabilities = DEFAULT_BASE_CAPABILITIES;
    private RpcMapping rpcMapping = new RpcMappingDefaultImpl();

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

    public RpcMapping getRpcMapping() {
        return rpcMapping;
    }

    public void setRpcMapping(RpcMapping rpcMapping) {
        this.rpcMapping = rpcMapping;
    }

}
