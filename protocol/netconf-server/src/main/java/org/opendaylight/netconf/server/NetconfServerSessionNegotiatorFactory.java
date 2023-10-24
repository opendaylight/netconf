/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.netty.channel.Channel;
import io.netty.util.Timer;
import io.netty.util.concurrent.Promise;
import java.net.SocketAddress;
import java.util.Set;
import org.checkerframework.checker.index.qual.NonNegative;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.nettyutil.NetconfSessionNegotiator;
import org.opendaylight.netconf.server.api.SessionIdProvider;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.osgi.NetconfOperationRouterImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;

// non-final for testing and netconf-testtool (for some reason)
public class NetconfServerSessionNegotiatorFactory {
    public static final Set<String> DEFAULT_BASE_CAPABILITIES = ImmutableSet.of(
        CapabilityURN.BASE,
        CapabilityURN.BASE_1_1,
        CapabilityURN.EXI,
        CapabilityURN.NOTIFICATION);

    private final @NonNegative int maximumIncomingChunkSize;
    private final Timer timer;
    private final SessionIdProvider idProvider;
    private final NetconfOperationServiceFactory aggregatedOpService;
    private final long connectionTimeoutMillis;
    private final NetconfMonitoringService monitoringService;
    private final Set<String> baseCapabilities;

    // FIXME: 5.0.0: protected
    public NetconfServerSessionNegotiatorFactory(final Timer timer,
            final NetconfOperationServiceFactory netconfOperationProvider, final SessionIdProvider idProvider,
            final long connectionTimeoutMillis, final NetconfMonitoringService monitoringService) {
        this(timer, netconfOperationProvider, idProvider, connectionTimeoutMillis, monitoringService, null);
    }

    // FIXME: 5.0.0: protected
    public NetconfServerSessionNegotiatorFactory(final Timer timer,
            final NetconfOperationServiceFactory netconfOperationProvider, final SessionIdProvider idProvider,
            final long connectionTimeoutMillis,  final NetconfMonitoringService monitoringService,
            final Set<String> baseCapabilities) {
        this(timer, netconfOperationProvider, idProvider, connectionTimeoutMillis, monitoringService, baseCapabilities,
            NetconfSessionNegotiator.DEFAULT_MAXIMUM_INCOMING_CHUNK_SIZE);
    }

    protected NetconfServerSessionNegotiatorFactory(final Timer timer,
            final NetconfOperationServiceFactory netconfOperationProvider, final SessionIdProvider idProvider,
            final long connectionTimeoutMillis, final NetconfMonitoringService monitoringService,
            final Set<String> baseCapabilities, final @NonNegative int maximumIncomingChunkSize) {
        this.timer = timer;
        aggregatedOpService = netconfOperationProvider;
        this.idProvider = idProvider;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.monitoringService = monitoringService;
        this.maximumIncomingChunkSize = maximumIncomingChunkSize;
        this.baseCapabilities = validateBaseCapabilities(baseCapabilities == null ? DEFAULT_BASE_CAPABILITIES :
                baseCapabilities);
    }

    private static ImmutableSet<String> validateBaseCapabilities(final Set<String> baseCapabilities) {
        // Check base capabilities to be supported by the server
        final var unknownBaseCaps = Sets.difference(baseCapabilities, DEFAULT_BASE_CAPABILITIES);
        Preconditions.checkArgument(unknownBaseCaps.isEmpty(),
                "Base capabilities that will be supported by netconf server have to be subset of %s, "
                        + "unknown base capabilities: %s",
                DEFAULT_BASE_CAPABILITIES, unknownBaseCaps);

        return ImmutableSet.<String>builder()
            .addAll(baseCapabilities)
            // Base 1.0 capability is supported by default
            .add(CapabilityURN.BASE)
            .build();
    }

    /**
     * Get session negotiator.
     *
     * @param channel                       Underlying channel
     * @param promise                       Promise to be notified
     * @return session negotiator
     */
    public NetconfServerSessionNegotiator getSessionNegotiator(final Channel channel,
            final Promise<NetconfServerSession> promise) {
        final var sessionId = idProvider.getNextSessionId();
        final var socketAddress = channel.parent() == null ? null : channel.parent().localAddress();
        final var service = getOperationServiceForAddress(sessionId, socketAddress);
        final var listener = new NetconfServerSessionListener(
            new NetconfOperationRouterImpl(service, monitoringService, sessionId), monitoringService, service);

        return new NetconfServerSessionNegotiator(createHelloMessage(sessionId, monitoringService), sessionId, promise,
            channel, timer, listener, connectionTimeoutMillis, maximumIncomingChunkSize);
    }

    protected NetconfOperationService getOperationServiceForAddress(final SessionIdType sessionId,
                                                                    final SocketAddress socketAddress) {
        return aggregatedOpService.createService(sessionId);
    }

    protected final NetconfOperationServiceFactory getOperationServiceFactory() {
        return aggregatedOpService;
    }

    private HelloMessage createHelloMessage(final SessionIdType sessionId,
            final NetconfMonitoringService capabilityProvider) {
        return HelloMessage.createServerHello(Sets.union(
            transformCapabilities(capabilityProvider.getCapabilities()), baseCapabilities),
            sessionId);
    }

    public static Set<String> transformCapabilities(final Capabilities capabilities) {
        return Sets.newHashSet(Collections2.transform(capabilities.getCapability(), Uri::getValue));
    }
}
