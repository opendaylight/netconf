/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.model;

public class Node {
    private String nodeId;
    private String connectionStatus;
    private String username;
    private String password;
    private String host;
    private Integer keepaliveDelay;
    private Integer port;
    private Boolean schemaless;
    private Boolean tcpOnly;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(final String nodeId) {
        this.nodeId = nodeId;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(final String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public Integer getKeepaliveDelay() {
        return keepaliveDelay;
    }

    public void setKeepaliveDelay(final Integer keepaliveDelay) {
        this.keepaliveDelay = keepaliveDelay;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(final Integer port) {
        this.port = port;
    }

    public Boolean getSchemaless() {
        return schemaless;
    }

    public void setSchemaless(final Boolean schemaless) {
        this.schemaless = schemaless;
    }

    public Boolean getTcpOnly() {
        return tcpOnly;
    }

    public void setTcpOnly(final Boolean tcpOnly) {
        this.tcpOnly = tcpOnly;
    }
}