/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol.tls;

import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.client.SslHandlerFactory;

public class NetconfCallHomeTlsServerBuilder {
    private String host = "0.0.0.0";
    private Integer port = 4335;
    private Integer timeout = 10;
    private Integer maxConnections = 64;
    private SslHandlerFactory sslHandlerFactory;
    private CallHomeNetconfSubsystemListener subsystemListener;
    private TlsAllowedDevicesMonitor allowedDevicesMonitor;

    public NetconfCallHomeTlsServerBuilder setHost(String host) {
        this.host = host;
        return this;
    }

    public NetconfCallHomeTlsServerBuilder setPort(Integer port) {
        this.port = port;
        return this;
    }

    public NetconfCallHomeTlsServerBuilder setTimeout(Integer timeout) {
        this.timeout = timeout;
        return this;
    }

    public NetconfCallHomeTlsServerBuilder setMaxConnections(Integer maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    public NetconfCallHomeTlsServerBuilder setSslHandlerFactory(SslHandlerFactory sslHandlerFactory) {
        this.sslHandlerFactory = sslHandlerFactory;
        return this;
    }

    public NetconfCallHomeTlsServerBuilder setSubsystemListener(CallHomeNetconfSubsystemListener subsystemListener) {
        this.subsystemListener = subsystemListener;
        return this;
    }

    public NetconfCallHomeTlsServerBuilder setAllowedDevicesMonitor(TlsAllowedDevicesMonitor allowedDevicesMonitor) {
        this.allowedDevicesMonitor = allowedDevicesMonitor;
        return this;
    }

    public NetconfCallHomeTlsServer build() {
        return new NetconfCallHomeTlsServer(host, port, timeout, maxConnections, sslHandlerFactory, subsystemListener,
            allowedDevicesMonitor);
    }
}