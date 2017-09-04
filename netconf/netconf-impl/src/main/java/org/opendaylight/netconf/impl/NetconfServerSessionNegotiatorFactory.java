/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.impl;

import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.netty.channel.Channel;
import io.netty.util.Timer;
import io.netty.util.concurrent.Promise;
import java.net.SocketAddress;
import java.util.Set;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfServerSessionPreferences;
import org.opendaylight.netconf.api.messages.NetconfHelloMessage;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.impl.osgi.NetconfOperationRouter;
import org.opendaylight.netconf.impl.osgi.NetconfOperationRouterImpl;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.protocol.framework.SessionListenerFactory;
import org.opendaylight.protocol.framework.SessionNegotiator;
import org.opendaylight.protocol.framework.SessionNegotiatorFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfServerSessionNegotiatorFactory implements SessionNegotiatorFactory<NetconfHelloMessage,
        NetconfServerSession, NetconfServerSessionListener> {

    public static final Set<String> DEFAULT_BASE_CAPABILITIES = ImmutableSet.of(
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1,
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_CAPABILITY_EXI_1_0
    );

    private final Timer timer;

    private final SessionIdProvider idProvider;
    private final NetconfOperationServiceFactory aggregatedOpService;
    private final long connectionTimeoutMillis;
    private final NetconfMonitoringService monitoringService;
    private static final Logger LOG = LoggerFactory.getLogger(NetconfServerSessionNegotiatorFactory.class);
    private final Set<String> baseCapabilities;

    public NetconfServerSessionNegotiatorFactory(final Timer timer,
                                                 final NetconfOperationServiceFactory netconfOperationProvider,
                                                 final SessionIdProvider idProvider, final long connectionTimeoutMillis,
                                                 final NetconfMonitoringService monitoringService,
                                                 final Set<String> baseCapabilities) {
        this.timer = timer;
        this.aggregatedOpService = netconfOperationProvider;
        this.idProvider = idProvider;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.monitoringService = monitoringService;
        this.baseCapabilities = validateBaseCapabilities(baseCapabilities == null ? DEFAULT_BASE_CAPABILITIES :
                baseCapabilities);
    }


    private static ImmutableSet<String> validateBaseCapabilities(final Set<String> baseCapabilities) {
        // Check base capabilities to be supported by the server
        final Sets.SetView<String> unknownBaseCaps = Sets.difference(baseCapabilities, DEFAULT_BASE_CAPABILITIES);
        Preconditions.checkArgument(unknownBaseCaps.isEmpty(),
                "Base capabilities that will be supported by netconf server have to be subset of %s, "
                        + "unknown base capabilities: %s",
                DEFAULT_BASE_CAPABILITIES, unknownBaseCaps);

        final ImmutableSet.Builder<String> b = ImmutableSet.builder();
        b.addAll(baseCapabilities);
        // Base 1.0 capability is supported by default
        b.add(XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0);
        return b.build();
    }

    /**
     * Get session negotiator.
     *
     * @param defunctSessionListenerFactory will not be taken into account as session listener factory can
     *                                      only be created after snapshot is opened, thus this method constructs
     *                                      proper session listener factory.
     * @param channel                       Underlying channel
     * @param promise                       Promise to be notified
     * @return session negotiator
     */
    @Override
    public SessionNegotiator<NetconfServerSession> getSessionNegotiator(
            final SessionListenerFactory<NetconfServerSessionListener> defunctSessionListenerFactory,
            final Channel channel, final Promise<NetconfServerSession> promise) {
        final long sessionId = idProvider.getNextSessionId();

        NetconfServerSessionPreferences proposal;
        try {
            proposal = new NetconfServerSessionPreferences(createHelloMessage(sessionId, monitoringService), sessionId);
        } catch (final NetconfDocumentedException e) {
            LOG.error("Unable to create hello message for session {} with {}", sessionId, monitoringService);
            throw new IllegalStateException(e);
        }

        return new NetconfServerSessionNegotiator(proposal, promise, channel, timer,
                getListener(Long.toString(sessionId), channel.parent().localAddress()), connectionTimeoutMillis);
    }

    private NetconfServerSessionListener getListener(final String netconfSessionIdForReporting,
                                                     final SocketAddress socketAddress) {
        final NetconfOperationService service = getOperationServiceForAddress(netconfSessionIdForReporting,
                socketAddress);
        final NetconfOperationRouter operationRouter =
                new NetconfOperationRouterImpl(service, monitoringService, netconfSessionIdForReporting);
        return new NetconfServerSessionListener(operationRouter, monitoringService, service);

    }

    protected NetconfOperationService getOperationServiceForAddress(final String netconfSessionIdForReporting,
                                                                    final SocketAddress socketAddress) {
        return this.aggregatedOpService.createService(netconfSessionIdForReporting);
    }

    protected final NetconfOperationServiceFactory getOperationServiceFactory() {
        return aggregatedOpService;
    }

    private NetconfHelloMessage createHelloMessage(
            final long sessionId, final NetconfMonitoringService capabilityProvider) throws NetconfDocumentedException {
        return NetconfHelloMessage.createServerHello(Sets.union(transformCapabilities(capabilityProvider
                .getCapabilities()), baseCapabilities), sessionId);
    }

    public static Set<String> transformCapabilities(final Capabilities capabilities) {
        return Sets.newHashSet(Collections2.transform(capabilities.getCapability(), Uri::getValue));
    }

}
