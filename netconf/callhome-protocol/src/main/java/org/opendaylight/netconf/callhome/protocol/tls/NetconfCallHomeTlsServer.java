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
import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;

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
                    protected void initChannel(final SocketChannel channel) {
                        CallHomeProtocolSessionContext tlsSessionContext = new CallHomeTlsSessionContext(channel,
                            sslHandlerFactory, subsystemListener);
                        ((CallHomeTlsSessionContext)tlsSessionContext).openNetconfChannel(channel);
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
