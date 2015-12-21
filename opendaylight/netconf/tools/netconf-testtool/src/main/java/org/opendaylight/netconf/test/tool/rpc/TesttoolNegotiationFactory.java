/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.rpc;

import io.netty.channel.Channel;
import io.netty.util.Timer;
import io.netty.util.concurrent.Promise;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfServerSessionPreferences;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.impl.NetconfServerSession;
import org.opendaylight.netconf.impl.NetconfServerSessionListener;
import org.opendaylight.netconf.impl.NetconfServerSessionNegotiator;
import org.opendaylight.netconf.impl.NetconfServerSessionNegotiatorFactory;
import org.opendaylight.netconf.impl.SessionIdProvider;
import org.opendaylight.netconf.impl.osgi.NetconfOperationRouter;
import org.opendaylight.netconf.impl.osgi.NetconfOperationRouterImpl;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.protocol.framework.SessionListenerFactory;
import org.opendaylight.protocol.framework.SessionNegotiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TesttoolNegotiationFactory extends NetconfServerSessionNegotiatorFactory {

    private static final Logger LOG = LoggerFactory.getLogger(TesttoolNegotiationFactory.class);

    private final Map<SocketAddress, NetconfOperationService> cachedOperationServices = new HashMap<>();

    public TesttoolNegotiationFactory(Timer timer, NetconfOperationServiceFactory netconfOperationProvider, SessionIdProvider idProvider, long connectionTimeoutMillis, NetconfMonitoringService monitoringService) {
        super(timer, netconfOperationProvider, idProvider, connectionTimeoutMillis, monitoringService);
    }

    public TesttoolNegotiationFactory(Timer timer, NetconfOperationServiceFactory netconfOperationProvider, SessionIdProvider idProvider, long connectionTimeoutMillis, NetconfMonitoringService monitoringService, Set<String> baseCapabilities) {
        super(timer, netconfOperationProvider, idProvider, connectionTimeoutMillis, monitoringService, baseCapabilities);
    }

    @Override
    public SessionNegotiator<NetconfServerSession> getSessionNegotiator(SessionListenerFactory<NetconfServerSessionListener> defunctSessionListenerFactory, Channel channel, Promise<NetconfServerSession> promise) {
        final long sessionId = idProvider.getNextSessionId();

        NetconfServerSessionPreferences proposal;
        try {
            proposal = new NetconfServerSessionPreferences(createHelloMessage(sessionId, monitoringService), sessionId);
        } catch (final NetconfDocumentedException e) {
            LOG.error("Unable to create hello message for session {} with {}", sessionId, monitoringService);
            throw new IllegalStateException(e);
        }

        return new NetconfServerSessionNegotiator(proposal, promise, channel, timer,
                getListener(Long.toString(sessionId), channel.localAddress()), connectionTimeoutMillis);
    }

    private NetconfServerSessionListener getListener(String netconfSessionIdForReporting, SocketAddress localAddress) {
        final NetconfOperationService service;
        if (cachedOperationServices.containsKey(localAddress)) {
            service = cachedOperationServices.get(localAddress);
        } else {
            service = this.aggregatedOpService.createService(netconfSessionIdForReporting);
            cachedOperationServices.put(localAddress, service);
        }
        final NetconfOperationRouter operationRouter =
                new NetconfOperationRouterImpl(service, monitoringService, netconfSessionIdForReporting);
        return new NetconfServerSessionListener(operationRouter, monitoringService, service);
    }
}
