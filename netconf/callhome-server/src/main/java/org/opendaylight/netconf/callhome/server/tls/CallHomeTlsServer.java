/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server.tls;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.ChannelOption;
import io.netty.util.HashedWheelTimer;
import java.net.InetAddress;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.callhome.server.CallHomeTransportChannelListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.netconf.transport.tls.TLSClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev230417.netconf.client.listen.stack.grouping.transport.ssh.ssh.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;

public final class CallHomeTlsServer implements AutoCloseable {
    private static final int DEFAULT_PORT = 4335;
    private static final Integer DEFAULT_TIMEOUT_MILLIS = 5000;
    private static final Integer DEFAULT_MAX_CONNECTIONS = 64;

    private final CallHomeTlsSessionContextManager contextMgr;
    private final TLSClient client;

    @VisibleForTesting
    CallHomeTlsServer(final TcpServerGrouping tcpServerParams,
            final BootstrapFactory bootstrapFactory,
            final Integer maxConnections, final Integer timeoutMillis,
            final NetconfClientSessionNegotiatorFactory negotiationFactory,
            final CallHomeTlsSessionContextManager contextMgr,
            final CallHomeTlsAuthProvider authProvider) {
        this.contextMgr = requireNonNull(contextMgr);
        final var bootstrap = bootstrapFactory.newServerBootstrap()
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.SO_BACKLOG, requireNonNull(maxConnections))
            .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, requireNonNull(timeoutMillis));
        final var transportChannelListener =
            new CallHomeTransportChannelListener(requireNonNull(negotiationFactory), contextMgr);
        try {
            client = TLSClient.listen(transportChannelListener, bootstrap, tcpServerParams, authProvider).get();
        } catch (UnsupportedConfigurationException | InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Could not start TLS Call-Home server", e);
        }
    }

    @Override
    public void close() throws Exception {
        contextMgr.close();
        client.shutdown().get();
    }

    public static class Builder {
        private final CallHomeTlsAuthProvider authProvider;
        private final CallHomeTlsSessionContextManager contextManager;

        private InetAddress address;
        private int port = DEFAULT_PORT;
        private BootstrapFactory bootstrapFactory;
        private NetconfClientSessionNegotiatorFactory negotiationFactory;
        private Integer maxConnections;
        private Integer timeoutMillis;

        public Builder(final CallHomeTlsSessionContextManager contextManager,
                final CallHomeTlsAuthProvider authProvider) {
            this.authProvider = authProvider;
            this.contextManager = contextManager;
        }

        public @NonNull CallHomeTlsServer build() {
            return new CallHomeTlsServer(
                toServerParams(address, port),
                bootstrapFactory == null ? defaultBootstrapFactory() : bootstrapFactory,
                maxConnections == null ? DEFAULT_MAX_CONNECTIONS : maxConnections,
                timeoutMillis == null ? DEFAULT_TIMEOUT_MILLIS : timeoutMillis,
                negotiationFactory == null ? defaultNegotiationFactory() : negotiationFactory,
                contextManager, authProvider);
        }

        public Builder withAddress(InetAddress newAddress) {
            this.address = newAddress;
            return this;
        }

        public Builder withPort(final int newPort) {
            this.port = newPort;
            return this;
        }

        public Builder withMaxConnections(final int newMaxConnections) {
            this.maxConnections = newMaxConnections;
            return this;
        }

        public Builder withTimeout(final int newTimeoutMillis) {
            this.timeoutMillis = newTimeoutMillis;
            return this;
        }

        public Builder withBootstrapFactory(final BootstrapFactory newBootstrapFactory) {
            this.bootstrapFactory = newBootstrapFactory;
            return this;
        }

        public Builder withNegotiationFactory(final NetconfClientSessionNegotiatorFactory newNegotiationFactory) {
            this.negotiationFactory = newNegotiationFactory;
            return this;
        }
    }

    private static TcpServerGrouping toServerParams(InetAddress address, int port) {
        final var ipAddress = IetfInetUtil.ipAddressFor(
            address == null ? InetAddress.getLoopbackAddress() : address);
        final var portNumber = new PortNumber(Uint16.valueOf(port < 0 ? DEFAULT_PORT : port));
        return new TcpServerParametersBuilder().setLocalAddress(ipAddress).setLocalPort(portNumber).build();
    }

    private static BootstrapFactory defaultBootstrapFactory() {
        return new BootstrapFactory("tls-call-home-server", 0);
    }

    private static NetconfClientSessionNegotiatorFactory defaultNegotiationFactory() {
        return new NetconfClientSessionNegotiatorFactory(new HashedWheelTimer(),
            Optional.empty(), DEFAULT_TIMEOUT_MILLIS);
    }
}