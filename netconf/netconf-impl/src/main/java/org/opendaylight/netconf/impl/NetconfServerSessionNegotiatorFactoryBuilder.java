/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.impl;

import io.netty.util.Timer;
import java.util.Set;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;

public class NetconfServerSessionNegotiatorFactoryBuilder {
    Timer timer;
    SessionIdProvider idProvider;
    NetconfOperationServiceFactory aggregatedOpService;
    long connectionTimeoutMillis;
    NetconfMonitoringService monitoringService;
    Set<String> baseCapabilities;

    public NetconfServerSessionNegotiatorFactoryBuilder(){
    }

    public NetconfServerSessionNegotiatorFactoryBuilder setTimer(final Timer timer){
        this.timer = timer;
        return this;
    }

    public NetconfServerSessionNegotiatorFactoryBuilder setIdProvider(final SessionIdProvider idProvider) {
        this.idProvider = idProvider;
        return this;
    }

    public NetconfServerSessionNegotiatorFactoryBuilder setAggregatedOpService(final NetconfOperationServiceFactory aggregatedOpService){
        this.aggregatedOpService = aggregatedOpService;
        return this;
    }

    public NetconfServerSessionNegotiatorFactoryBuilder setConnectionTimeoutMillis(final long connectionTimeoutMillis){
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        return this;
    }

    public NetconfServerSessionNegotiatorFactoryBuilder setMonitoringService(final NetconfMonitoringService monitoringService){
        this.monitoringService = monitoringService;
        return this;
    }

    public NetconfServerSessionNegotiatorFactoryBuilder setBaseCapabilities(final Set<String> baseCapabilities){
        this.baseCapabilities = baseCapabilities;
        return this;
    }


    public NetconfServerSessionNegotiatorFactory build(){
        return new NetconfServerSessionNegotiatorFactory(this);
    }
}
