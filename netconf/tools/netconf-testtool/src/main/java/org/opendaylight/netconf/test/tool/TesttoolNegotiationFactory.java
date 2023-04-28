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
import org.opendaylight.netconf.server.NetconfServerSessionNegotiatorFactory;
import org.opendaylight.netconf.server.api.SessionIdProvider;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TesttoolNegotiationFactory extends NetconfServerSessionNegotiatorFactory {
    private static final Logger LOG = LoggerFactory.getLogger(TesttoolNegotiationFactory.class);

    private final Map<SocketAddress, NetconfOperationService> cachedOperationServices = new HashMap<>();

    public TesttoolNegotiationFactory(final Timer timer, final NetconfOperationServiceFactory netconfOperationProvider,
            final SessionIdProvider idProvider, final long connectionTimeoutMillis,
            final NetconfMonitoringService monitoringService) {
        super(timer, netconfOperationProvider, idProvider, connectionTimeoutMillis, monitoringService,
            NetconfServerSessionNegotiatorFactory.DEFAULT_BASE_CAPABILITIES);
    }

    public TesttoolNegotiationFactory(final Timer timer, final NetconfOperationServiceFactory netconfOperationProvider,
            final SessionIdProvider idProvider, final long connectionTimeoutMillis,
            final NetconfMonitoringService monitoringService, final Set<String> baseCapabilities) {
        super(timer, netconfOperationProvider, idProvider, connectionTimeoutMillis,
            monitoringService, baseCapabilities);
    }

    @Override
    protected NetconfOperationService getOperationServiceForAddress(final SessionIdType sessionId,
            final SocketAddress socketAddress) {
        if (cachedOperationServices.containsKey(socketAddress)) {
            LOG.debug("Session {}: Getting cached operation service factory for test tool device on address {}",
                sessionId.getValue(), socketAddress);
            return cachedOperationServices.get(socketAddress);
        } else {
            final NetconfOperationService service = getOperationServiceFactory().createService(sessionId);
            cachedOperationServices.put(socketAddress, service);
            LOG.debug("Session {}: Creating new operation service factory for test tool device on address {}",
                sessionId.getValue(), socketAddress);
            return service;
        }
    }
}
