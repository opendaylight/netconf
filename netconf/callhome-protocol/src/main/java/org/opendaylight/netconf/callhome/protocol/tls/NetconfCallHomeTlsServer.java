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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class NetconfCallHomeTlsServer {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHomeTlsServer.class);

    private final String host;
    private final Integer port;
    private final Integer timeout;
    private final Integer maxConnections;
    private final SslConfigurationProvider sslConfigurationProvider;
    private final EventExecutor eventExecutor;

    private ChannelFuture cf;

    public NetconfCallHomeTlsServer(String host, Integer port, Integer timeout, Integer maxConnections,
                                    SslConfigurationProvider sslConfigurationProvider, EventExecutor eventExecutor) {
        this.host = requireNonNull(host);
        this.port = requireNonNull(port);
        this.timeout = requireNonNull(timeout);
        this.maxConnections = requireNonNull(maxConnections);
        this.sslConfigurationProvider = requireNonNull(sslConfigurationProvider);
        this.eventExecutor = eventExecutor;
    }

    public void setup() {
        NetconfClientSessionListener sessionListener = new NetconfTlsSessionListener();
        NetconfClientSessionNegotiatorFactory negotiatorFactory = new NetconfClientSessionNegotiatorFactory(new HashedWheelTimer(),
            Optional.empty(), TimeUnit.SECONDS.toMillis(5));

        TlsClientChannelInitializer tlsClientChannelInitializer = new TlsClientChannelInitializer(sslConfigurationProvider,
            negotiatorFactory, sessionListener);
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(group);
            b.channel(NioServerSocketChannel.class);
            b.localAddress(new InetSocketAddress(host, port));
            b.childOption(ChannelOption.SO_KEEPALIVE, true);
            b.childOption(ChannelOption.SO_BACKLOG, maxConnections);
            b.childHandler(
                new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        tlsClientChannelInitializer.initialize(ch, new DefaultPromise<>(eventExecutor));
                    }
                });
            cf = b.bind().sync();
            cf.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                group.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                LOG.error("Error during initialization of Call-Home TLS server", e);
            }
        }
    }

    public void destroy()  {
        try {
            cf.channel().close().sync();
        } catch (InterruptedException e) {
            LOG.error("Error during closing the server channel of Call-Home TLS server", e);
        }
    }
}
