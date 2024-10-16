/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelOption;
import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.netconf.transport.tls.TLSClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.listen.stack.grouping.transport.ssh.ssh.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.tcp.server.grouping.LocalBindBuilder;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.Uint16;

public final class CallHomeTlsServer implements AutoCloseable {
    private static final int DEFAULT_PORT = 4335;
    private static final Integer DEFAULT_TIMEOUT_MILLIS = 5000;
    private static final Integer DEFAULT_MAX_CONNECTIONS = 64;

    private final CallHomeTlsSessionContextManager contextManager;
    private final TLSClient client;

    private CallHomeTlsServer(final TcpServerGrouping tcpServerParams,
            final BootstrapFactory bootstrapFactory,
            final Integer maxConnections, final Integer timeoutMillis,
            final NetconfClientSessionNegotiatorFactory negotiationFactory,
            final CallHomeTlsSessionContextManager contextManager,
            final CallHomeTlsAuthProvider authProvider,
            final CallHomeStatusRecorder statusRecorder) {
        this.contextManager = requireNonNull(contextManager);
        final var bootstrap = bootstrapFactory.newServerBootstrap()
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.SO_BACKLOG, requireNonNull(maxConnections))
            .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, requireNonNull(timeoutMillis));
        final var transportChannelListener = new CallHomeTransportChannelListener(requireNonNull(negotiationFactory),
            contextManager, requireNonNull(statusRecorder));
        try {
            client = TLSClient.listen(transportChannelListener, bootstrap, tcpServerParams, authProvider)
                .get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (UnsupportedConfigurationException | InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not start TLS Call-Home server", e);
        }
    }

    @Override
    public void close() throws Exception {
        contextManager.close();
        client.shutdown().get();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private InetAddress address;
        private int port = DEFAULT_PORT;
        private BootstrapFactory bootstrapFactory;
        private NetconfClientSessionNegotiatorFactory negotiationFactory;
        private Integer maxConnections;
        private Integer timeoutMillis;
        private CallHomeTlsAuthProvider authProvider;
        private CallHomeTlsSessionContextManager contextManager;
        private CallHomeStatusRecorder statusRecorder;

        private Builder() {
            // on purpose
        }

        public @NonNull CallHomeTlsServer build() {
            timeoutMillis = timeoutMillis == null ? DEFAULT_TIMEOUT_MILLIS : timeoutMillis;
            return new CallHomeTlsServer(
                toServerParams(address, port),
                bootstrapFactory == null ? defaultBootstrapFactory() : bootstrapFactory,
                maxConnections == null ? DEFAULT_MAX_CONNECTIONS : maxConnections,
                timeoutMillis == null ? DEFAULT_TIMEOUT_MILLIS : timeoutMillis,
                negotiationFactory, contextManager, authProvider, statusRecorder);
        }

        public Builder withSessionContextManager(final CallHomeTlsSessionContextManager newContextManager) {
            contextManager = newContextManager;
            return this;
        }

        public Builder withAuthProvider(final CallHomeTlsAuthProvider newAuthProvider) {
            authProvider = newAuthProvider;
            return this;
        }

        public Builder withStatusRecorder(final CallHomeStatusRecorder newStatusRecorder) {
            statusRecorder = newStatusRecorder;
            return this;
        }

        public Builder withAddress(final InetAddress newAddress) {
            address = newAddress;
            return this;
        }

        public Builder withPort(final int newPort) {
            port = newPort;
            return this;
        }

        public Builder withMaxConnections(final int newMaxConnections) {
            maxConnections = newMaxConnections;
            return this;
        }

        public Builder withTimeout(final int newTimeoutMillis) {
            timeoutMillis = newTimeoutMillis;
            return this;
        }

        public Builder withBootstrapFactory(final BootstrapFactory newBootstrapFactory) {
            bootstrapFactory = newBootstrapFactory;
            return this;
        }

        public Builder withNegotiationFactory(final NetconfClientSessionNegotiatorFactory newNegotiationFactory) {
            negotiationFactory = newNegotiationFactory;
            return this;
        }
    }

    private static TcpServerGrouping toServerParams(final InetAddress address, final int port) {
        final var ipAddress = IetfInetUtil.ipAddressFor(
            address == null ? InetAddress.getLoopbackAddress() : address);
        final var portNumber = new PortNumber(Uint16.valueOf(port < 0 ? DEFAULT_PORT : port));
        return new TcpServerParametersBuilder()
            .setLocalBind(BindingMap.of(new LocalBindBuilder()
                .setLocalAddress(ipAddress)
                .setLocalPort(portNumber)
                .build()))
            .build();
    }

    private static BootstrapFactory defaultBootstrapFactory() {
        return new BootstrapFactory("tls-call-home-server", 0);
    }
}
