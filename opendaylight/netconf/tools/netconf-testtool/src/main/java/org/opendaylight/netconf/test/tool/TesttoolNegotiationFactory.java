/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import io.netty.util.Timer;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.impl.NetconfServerSessionNegotiatorFactory;
import org.opendaylight.netconf.impl.SessionIdProvider;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;

public class TesttoolNegotiationFactory extends NetconfServerSessionNegotiatorFactory {

    private final Map<SocketAddress, NetconfOperationService> cachedOperationServices = new HashMap<>();

    public TesttoolNegotiationFactory(final Timer timer, final NetconfOperationServiceFactory netconfOperationProvider,
                                      final SessionIdProvider idProvider, final long connectionTimeoutMillis,
                                      final NetconfMonitoringService monitoringService) {
        super(timer, netconfOperationProvider, idProvider, connectionTimeoutMillis, monitoringService);
    }

    public TesttoolNegotiationFactory(final Timer timer, final NetconfOperationServiceFactory netconfOperationProvider,
                                      final SessionIdProvider idProvider, final long connectionTimeoutMillis,
                                      final NetconfMonitoringService monitoringService, final Set<String> baseCapabilities) {
        super(timer, netconfOperationProvider, idProvider, connectionTimeoutMillis, monitoringService, baseCapabilities);
    }

    @Override
    protected NetconfOperationService getOperationServiceForAddress(final String netconfSessionIdForReporting, final SocketAddress socketAddress) {
        if (cachedOperationServices.containsKey(socketAddress)) {
            return cachedOperationServices.get(socketAddress);
        } else {
            final NetconfOperationService service = getOperationServiceFactory().createService(netconfSessionIdForReporting);
            cachedOperationServices.put(socketAddress, service);
            return service;
        }
    }
}
