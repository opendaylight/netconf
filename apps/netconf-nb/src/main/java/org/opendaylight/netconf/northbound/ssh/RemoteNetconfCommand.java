/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound.ssh;

import static java.util.Objects.requireNonNull;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.shaded.sshd.common.io.IoInputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoOutputStream;
import org.opendaylight.netconf.shaded.sshd.server.Environment;
import org.opendaylight.netconf.shaded.sshd.server.ExitCallback;
import org.opendaylight.netconf.shaded.sshd.server.channel.ChannelSession;
import org.opendaylight.netconf.shaded.sshd.server.command.AsyncCommand;
import org.opendaylight.netconf.shaded.sshd.server.command.Command;
import org.opendaylight.netconf.shaded.sshd.server.session.ServerSession;
import org.opendaylight.netconf.shaded.sshd.server.subsystem.SubsystemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This command handles all netconf related rpc and forwards to delegate server.
 * Uses netty to make a local connection to delegate server.
 *
 * <p>
 * Command is Apache Mina SSH terminology for objects handling ssh data.
 */
final class RemoteNetconfCommand implements AsyncCommand {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteNetconfCommand.class);

    private final EventLoopGroup clientEventGroup;
    private final LocalAddress localAddress;

    private IoInputStream in;
    private IoOutputStream out;
    private ExitCallback callback;
    private NetconfHelloMessageAdditionalHeader netconfHelloMessageAdditionalHeader;

    private Channel clientChannel;
    private ChannelFuture clientChannelFuture;

    RemoteNetconfCommand(final EventLoopGroup clientEventGroup, final LocalAddress localAddress) {
        this.clientEventGroup = clientEventGroup;
        this.localAddress = localAddress;
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public void setIoInputStream(final IoInputStream in) {
        this.in = in;
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public void setIoOutputStream(final IoOutputStream out) {
        this.out = out;
    }

    @Override
    public void setIoErrorStream(final IoOutputStream err) {
        // TODO do we want to use error stream in some way ?
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public void setInputStream(final InputStream in) {
        throw new UnsupportedOperationException("Synchronous IO is unsupported");
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public void setOutputStream(final OutputStream out) {
        throw new UnsupportedOperationException("Synchronous IO is unsupported");

    }

    @Override
    public void setErrorStream(final OutputStream err) {
        throw new UnsupportedOperationException("Synchronous IO is unsupported");

    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public void setExitCallback(final ExitCallback callback) {
        this.callback = callback;
    }

    @Override
    public void start(final ChannelSession channel, final Environment env) {
        final ServerSession session = channel.getServerSession();
        final SocketAddress remoteAddress = session.getIoSession().getRemoteAddress();
        final String hostName;
        final String port;
        if (remoteAddress instanceof InetSocketAddress remoteInetAddress) {
            hostName = remoteInetAddress.getAddress().getHostAddress();
            port = Integer.toString(remoteInetAddress.getPort());
        } else {
            hostName = "";
            port = "";
        }
        netconfHelloMessageAdditionalHeader = new NetconfHelloMessageAdditionalHeader(session.getUsername(), hostName,
            port, "ssh", "client");

        LOG.trace("Establishing internal connection to netconf server for client: {}", getClientAddress());

        final Bootstrap clientBootstrap = new Bootstrap();
        clientBootstrap.group(clientEventGroup).channel(LocalChannel.class);

        clientBootstrap.handler(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(final LocalChannel ch) {
                ch.pipeline()
                        .addLast(new SshProxyClientHandler(in, out, netconfHelloMessageAdditionalHeader, callback));
            }
        });
        clientChannelFuture = clientBootstrap.connect(localAddress);
        clientChannelFuture.addListener(future -> {
            if (future.isSuccess()) {
                clientChannel = clientChannelFuture.channel();
            } else {
                LOG.warn("Unable to establish internal connection to netconf server for client: {}",
                        getClientAddress());
                requireNonNull(callback, "Exit callback must be set").onExit(1,
                    "Unable to establish internal connection to netconf server for client: " + getClientAddress());
            }
        });
    }

    @Override
    public void destroy(final ChannelSession channel) {
        LOG.trace("Releasing internal connection to netconf server for client: {} on channel: {}",
                getClientAddress(), clientChannel);

        clientChannelFuture.cancel(true);
        if (clientChannel != null) {
            clientChannel.close().addListener(future -> {
                if (!future.isSuccess()) {
                    LOG.warn("Unable to release internal connection to netconf server on channel: {}",
                            clientChannel);
                }
            });
        }
    }

    private String getClientAddress() {
        return netconfHelloMessageAdditionalHeader.getAddress();
    }

    static class NetconfCommandFactory implements SubsystemFactory {
        public static final String NETCONF = "netconf";

        private final EventLoopGroup clientBootstrap;
        private final LocalAddress localAddress;

        NetconfCommandFactory(final EventLoopGroup clientBootstrap, final LocalAddress localAddress) {
            this.clientBootstrap = clientBootstrap;
            this.localAddress = localAddress;
        }

        @Override
        public String getName() {
            return NETCONF;
        }

        @Override
        public Command createSubsystem(final ChannelSession channel) {
            return new RemoteNetconfCommand(clientBootstrap, localAddress);
        }
    }
}
