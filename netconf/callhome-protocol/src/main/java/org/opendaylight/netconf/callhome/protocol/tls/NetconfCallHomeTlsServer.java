/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol.tls;

import static java.util.Objects.requireNonNull;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Optional;
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfCallHomeTlsServer {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHomeTlsServer.class);

    private final String host;
    private final Integer port;
    private final Integer timeout;
    private final Integer maxConnections;
    private final SslHandlerFactory sslHandlerFactory;
    private final CallHomeNetconfSubsystemListener subsystemListener;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final TlsAllowedDevicesMonitor allowedDevicesMonitor;

    private ChannelFuture cf;

    NetconfCallHomeTlsServer(final String host, final Integer port, final Integer timeout, final Integer maxConnections,
                             final SslHandlerFactory sslHandlerFactory,
                             final CallHomeNetconfSubsystemListener subsystemListener,
                             final EventLoopGroup bossGroup, final EventLoopGroup workerGroup,
                             final TlsAllowedDevicesMonitor allowedDevicesMonitor) {
        this.host = requireNonNull(host);
        this.port = requireNonNull(port);
        this.timeout = requireNonNull(timeout);
        this.maxConnections = requireNonNull(maxConnections);
        this.sslHandlerFactory = requireNonNull(sslHandlerFactory);
        this.subsystemListener = requireNonNull(subsystemListener);
        this.bossGroup = requireNonNull(bossGroup);
        this.workerGroup = requireNonNull(workerGroup);
        this.allowedDevicesMonitor = requireNonNull(allowedDevicesMonitor);
    }

    public void start() {
        final ChannelFuture bindFuture = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            // FIXME: a case against using globals: we really would like to use epoll() here
            .channel(NioServerSocketChannel.class)
            .localAddress(new InetSocketAddress(host, port))
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.SO_BACKLOG, maxConnections)
            .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
            .childHandler(new TlsAuthChannelInitializer(sslHandlerFactory, handshakeListener))
            .bind();
        bindFuture.addListener(bindListener);
    }

    GenericFutureListener<Future<Channel>> handshakeListener = new GenericFutureListener<>() {
        @Override
        public void operationComplete(final Future<Channel> future) throws Exception {
            if (future.isSuccess()) {
                LOG.debug("SSL handshake completed successfully, accepting connection...");
                final Channel channel = future.get();
                // If the ssl handshake was successful it is expected that session contains peer certificate(s)
                final Certificate cert = channel.pipeline().get(SslHandler.class).engine().getSession()
                    .getPeerCertificates()[0];
                final PublicKey publicKey = cert.getPublicKey();
                final Optional<String> deviceId = allowedDevicesMonitor.findDeviceIdByPublicKey(publicKey);
                if (deviceId.isEmpty()) {
                    LOG.error("Unable to identify connected device by provided certificate");
                    channel.close();
                } else {
                    final CallHomeTlsSessionContext tlsSessionContext = new CallHomeTlsSessionContext(deviceId.get(),
                        channel, sslHandlerFactory, subsystemListener);
                    tlsSessionContext.openNetconfChannel(channel);
                }
            } else {
                LOG.debug("SSL handshake failed, rejecting connection...");
                future.get().close();
            }
        }
    };

    GenericFutureListener<ChannelFuture> bindListener = future -> {
        if (future.isSuccess()) {
            LOG.debug("Call-Home TLS server bind completed");
        } else {
            LOG.error("Call-Home TLS server bind failed", future.cause());
        }
        cf = future.channel().closeFuture().addListener(f -> stop());
    };

    public void stop() {
        LOG.debug("Stopping the Call-Home TLS server...");
        try {
            if (cf != null && cf.channel().isOpen()) {
                cf.channel().close().sync();
            }
        } catch (final InterruptedException e) {
            LOG.error("Error during shutdown of the Call-Home TLS server", e);
        }
    }
}