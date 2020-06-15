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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfCallHomeTlsServer {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHomeTlsServer.class);

    private final String host;
    private final Integer port;
    private final Integer timeout;
    private final Integer maxConnections;
    private final SslHandlerFactory sslHandlerFactory;
    private final EventExecutor eventExecutor;
    private final CallHomeNetconfSubsystemListener subsystemListener;
    private ChannelFuture cf;
    private EventLoopGroup group = new NioEventLoopGroup();

    public NetconfCallHomeTlsServer(String host, Integer port, Integer timeout, Integer maxConnections,
                                    SslHandlerFactory sslHandlerFactory, EventExecutor eventExecutor,
                                    CallHomeNetconfSubsystemListener subsystemListener) {
        this.host = requireNonNull(host);
        this.port = requireNonNull(port);
        this.timeout = requireNonNull(timeout);
        this.maxConnections = requireNonNull(maxConnections);
        this.sslHandlerFactory = requireNonNull(sslHandlerFactory);
        this.eventExecutor = requireNonNull(eventExecutor);
        this.subsystemListener = requireNonNull(subsystemListener);
    }

    public void setup() {
        try {
            ServerBootstrap bs = new ServerBootstrap();
            bs.group(group);
            bs.channel(NioServerSocketChannel.class);
            bs.localAddress(new InetSocketAddress(host, port));
            bs.childOption(ChannelOption.SO_KEEPALIVE, true);
            bs.childOption(ChannelOption.SO_BACKLOG, maxConnections);
            bs.childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout);
            bs.childHandler(new TlsAuthChannelInitializer(sslHandlerFactory, handshakeListener));
            cf = bs.bind().sync();
            cf.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            LOG.error("Call-Home TLS server startup failed: {}", e.getLocalizedMessage());
        } finally {
            try {
                group.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                LOG.error("Error during initialization of Call-Home TLS server", e);
            }
        }
    }

    GenericFutureListener<Future<Channel>> handshakeListener = new GenericFutureListener<>() {
        @Override
        public void operationComplete(Future<Channel> future) throws Exception {
            if (future.isSuccess()) {
                LOG.debug("SSL handshake completed successfully, accepting connection...");
                Channel channel = future.get();
                CallHomeTlsSessionContext tlsSessionContext = new CallHomeTlsSessionContext(channel,
                    sslHandlerFactory, subsystemListener);
                tlsSessionContext.openNetconfChannel(channel);

            } else {
                LOG.debug("SSL handshake failed, rejecting connection...");
                future.get().close();
            }
        }
    };

    public void destroy()  {
        try {
            group.shutdownGracefully().get(5L, TimeUnit.SECONDS);
            cf.channel().close().sync();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOG.error("Error during TLS-based Call-Home server {}", e.getLocalizedMessage());
        }
    }
}