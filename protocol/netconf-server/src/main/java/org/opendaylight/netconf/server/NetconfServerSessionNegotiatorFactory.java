/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.index.qual.NonNegative;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.nettyutil.NetconfSessionNegotiator;
import org.opendaylight.netconf.server.api.SessionIdProvider;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.osgi.NetconfOperationRouterImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;

// non-final for testing and netconf-testtool (for some reason)
public class NetconfServerSessionNegotiatorFactory {
    public static final ImmutableSet<String> DEFAULT_BASE_CAPABILITIES = ImmutableSet.of(
        CapabilityURN.BASE,
        CapabilityURN.BASE_1_1,
        CapabilityURN.EXI,
        CapabilityURN.NOTIFICATION);

    private final @NonNegative int maximumIncomingChunkSize;
    private final NetconfTimer timer;
    private final SessionIdProvider idProvider;
    private final NetconfOperationServiceFactory aggregatedOpService;
    private final long connectionTimeoutMillis;
    private final NetconfMonitoringService monitoringService;
    private final Set<String> baseCapabilities;

    protected NetconfServerSessionNegotiatorFactory(final NetconfTimer timer,
            final NetconfOperationServiceFactory netconfOperationProvider, final SessionIdProvider idProvider,
            final long connectionTimeoutMillis,  final NetconfMonitoringService monitoringService,
            final Set<String> baseCapabilities) {
        this(timer, netconfOperationProvider, idProvider, connectionTimeoutMillis, monitoringService, baseCapabilities,
            NetconfSessionNegotiator.DEFAULT_MAXIMUM_INCOMING_CHUNK_SIZE);
    }

    private NetconfServerSessionNegotiatorFactory(final NetconfTimer timer,
            final NetconfOperationServiceFactory netconfOperationProvider, final SessionIdProvider idProvider,
            final long connectionTimeoutMillis, final NetconfMonitoringService monitoringService,
            final Set<String> baseCapabilities, final @NonNegative int maximumIncomingChunkSize) {
        this.timer = requireNonNull(timer);
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
        if (!unknownBaseCaps.isEmpty()) {
            throw new IllegalArgumentException(
                "Base capabilities that will be supported by netconf server have to be subset of "
                    + DEFAULT_BASE_CAPABILITIES + ", unknown base capabilities: " + unknownBaseCaps);
        }

        return ImmutableSet.<String>builder()
            .addAll(baseCapabilities)
            // Base 1.0 capability is supported by default
            .add(CapabilityURN.BASE)
            .build();
    }

    public static @NonNull Builder builder() {
        return new Builder();
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
        final var service = getOperationServiceForAddress(sessionId,
            channel.parent() == null ? null : channel.parent().localAddress());
        final var capabilities = new HashSet<>(baseCapabilities);
        for (var capability : monitoringService.getCapabilities().requireCapability()) {
            capabilities.add(capability.getValue());
        }

        return new NetconfServerSessionNegotiator(HelloMessage.createServerHello(capabilities, sessionId), sessionId,
            promise, channel, timer,
            new NetconfServerSessionListener(new NetconfOperationRouterImpl(service, monitoringService, sessionId),
                monitoringService, service),
            connectionTimeoutMillis, maximumIncomingChunkSize);
    }

    protected NetconfOperationService getOperationServiceForAddress(final SessionIdType sessionId,
            final SocketAddress socketAddress) {
        return aggregatedOpService.createService(sessionId);
    }

    protected final NetconfOperationServiceFactory getOperationServiceFactory() {
        return aggregatedOpService;
    }

    public static final class Builder {
        private @NonNegative int maximumIncomingChunkSize =
            NetconfSessionNegotiator.DEFAULT_MAXIMUM_INCOMING_CHUNK_SIZE;
        private NetconfTimer timer;
        private SessionIdProvider idProvider;
        private NetconfOperationServiceFactory aggregatedOpService;
        private long connectionTimeoutMillis;
        private NetconfMonitoringService monitoringService;
        private Set<String> baseCapabilities;

        private Builder() {
            // Hidden on purpose
        }

        public Builder setTimer(final NetconfTimer timer) {
            this.timer = requireNonNull(timer);
            return this;
        }

        public Builder setIdProvider(final SessionIdProvider idProvider) {
            this.idProvider = idProvider;
            return this;
        }

        public Builder setAggregatedOpService(
                final NetconfOperationServiceFactory aggregatedOpService) {
            this.aggregatedOpService = aggregatedOpService;
            return this;
        }

        public Builder setConnectionTimeoutMillis(final long connectionTimeoutMillis) {
            this.connectionTimeoutMillis = connectionTimeoutMillis;
            return this;
        }

        public Builder setMonitoringService(
                final NetconfMonitoringService monitoringService) {
            this.monitoringService = monitoringService;
            return this;
        }

        public Builder setBaseCapabilities(final Set<String> baseCapabilities) {
            this.baseCapabilities = baseCapabilities;
            return this;
        }

        public Builder setMaximumIncomingChunkSize(final @NonNegative int maximumIncomingChunkSize) {
            checkArgument(maximumIncomingChunkSize > 0);
            this.maximumIncomingChunkSize = maximumIncomingChunkSize;
            return this;
        }

        public NetconfServerSessionNegotiatorFactory build() {
            validate();
            return new NetconfServerSessionNegotiatorFactory(timer, aggregatedOpService, idProvider,
                    connectionTimeoutMillis, monitoringService, baseCapabilities, maximumIncomingChunkSize);
        }

        private void validate() {
            requireNonNull(timer, "timer not initialized");
            requireNonNull(aggregatedOpService, "NetconfOperationServiceFactory not initialized");
            requireNonNull(idProvider, "SessionIdProvider not initialized");
            checkArgument(connectionTimeoutMillis > 0, "connection time out <=0");
            requireNonNull(monitoringService, "NetconfMonitoringService not initialized");

            if (baseCapabilities == null) {
                baseCapabilities = NetconfServerSessionNegotiatorFactory.DEFAULT_BASE_CAPABILITIES;
            }
        }
    }
}
