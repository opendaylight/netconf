/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol.SSH;
import static org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol.TCP;
import static org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol.TLS;

import com.google.common.collect.ImmutableSet;
import io.netty.channel.EventLoopGroup;
import io.netty.util.Timer;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.shaded.sshd.client.channel.ChannelSubsystem;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.ClientSubsystemFactory;
import org.opendaylight.netconf.transport.ssh.SSHClient;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tls.TLSClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;

public class NetconfClientFactoryImpl implements NetconfClientFactory {
    private final EventLoopGroup group;
    private final Timer timer;

    public NetconfClientFactoryImpl(final EventLoopGroup group, final Timer timer) {
        this.group = requireNonNull(group);
        this.timer = requireNonNull(timer);
    }

    @Override
    public Future<NetconfClientSession> createClient(final NetconfClientConfiguration configuration)
            throws UnsupportedConfigurationException {
        final var protocol = configuration.getProtocol();
        final var promise = new DefaultPromise<NetconfClientSession>(GlobalEventExecutor.INSTANCE);
        final var channelInitializer = new ClientChannelInitializer(createNegotiatorFactory(configuration),
            () -> configuration.getSessionListener());
        final var bootstrap = NettyTransportSupport.newBootstrap().group(group);

        if (TCP.equals(protocol)) {
            TCPClient.connect(createTransportChannelListener(promise, channelInitializer), bootstrap,
                configuration.getTcpParameters());
        } else if (TLS.equals(protocol)) {
            TLSClient.connect(createTransportChannelListener(promise, channelInitializer), bootstrap,
                configuration.getTcpParameters(), configuration.getTlsParameters());
        } else if (SSH.equals(protocol)) {
            SSHClient.connect(createTransportChannelListener(promise, null), bootstrap,
                configuration.getTcpParameters(), configuration.getSshParameters(),
                createNetconfSubsystemFactory(promise, channelInitializer));
        }
        return promise;
    }

    private NetconfClientSessionNegotiatorFactory createNegotiatorFactory(
            final NetconfClientConfiguration configuration) {
        final var capabilities = configuration.getOdlHelloCapabilities();
        if (capabilities == null || capabilities.isEmpty()) {
            return new NetconfClientSessionNegotiatorFactory(timer, configuration.getAdditionalHeader(),
                configuration.getConnectionTimeoutMillis(), configuration.getMaximumIncomingChunkSize());
        }
        final var stringCapabilities = capabilities.stream().map(Uri::getValue)
            .collect(ImmutableSet.toImmutableSet());
        return new NetconfClientSessionNegotiatorFactory(timer, configuration.getAdditionalHeader(),
            configuration.getConnectionTimeoutMillis(), stringCapabilities);
    }

    private static TransportChannelListener createTransportChannelListener(
        final @NonNull Promise<NetconfClientSession> promise,
        final @Nullable ClientChannelInitializer channelInitializer) {

        return new TransportChannelListener() {
            @Override
            public void onTransportChannelEstablished(@NonNull TransportChannel channel) {
                if (channelInitializer != null) {
                    channelInitializer.initialize(channel.channel(), promise);
                }
            }

            @Override
            public void onTransportChannelFailed(@NonNull Throwable cause) {
                promise.setFailure(cause);
            }
        };
    }

    private static ClientSubsystemFactory createNetconfSubsystemFactory(
            final Promise<NetconfClientSession> promise, final ClientChannelInitializer channelInitializer) {
        return new ClientSubsystemFactory() {
            @Override
            public @NonNull String getSubsystemName() {
                return "netconf";
            }

            @Override
            public @NonNull ChannelSubsystem createSubsystemChannel() {
                return new NetconfChannelSubsystem(channelInitializer, promise);
            }
        };
    }
}
