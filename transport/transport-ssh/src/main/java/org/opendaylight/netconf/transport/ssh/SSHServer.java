/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.DefaultChannelGroup;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactoryFactory;
import org.opendaylight.netconf.shaded.sshd.server.subsystem.SubsystemFactory;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;

/**
 * A {@link TransportStack} acting as an SSH server.
 */
public final class SSHServer extends SSHTransportStack {
    private final TransportSshServer sshServer;

    private SSHServer(final TransportChannelListener listener, final TransportSshServer sshServer) {
        super(listener);
        this.sshServer = requireNonNull(sshServer);
        sshServer.addSessionListener(new UserAuthSessionListener(sessionAuthHandlers, sessions));
    }

    static SSHServer of(final NettyIoServiceFactoryFactory ioServiceFactory, final EventLoopGroup group,
            final TransportChannelListener listener, final SubsystemFactory subsystemFactory,
            final SshServerGrouping serverParams, final ServerFactoryManagerConfigurator configurator)
                throws UnsupportedConfigurationException {
        return new SSHServer(listener, new TransportSshServer.Builder(ioServiceFactory, group, subsystemFactory)
            .serverParams(serverParams)
            .configurator(configurator)
            .buildChecked());
    }

    @NonNull ListenableFuture<SSHServer> connect(final Bootstrap bootstrap, final TcpClientGrouping connectParams)
            throws UnsupportedConfigurationException {
        return transformUnderlay(this, TCPClient.connect(asListener(), bootstrap, connectParams));
    }

    @NonNull ListenableFuture<SSHServer> listen(final ServerBootstrap bootstrap, final TcpServerGrouping connectParams)
            throws UnsupportedConfigurationException {
        return transformUnderlay(this, TCPServer.listen(asListener(), bootstrap, connectParams));
    }

    @Override
    SshIoSession createIoSession(final Channel channel) {
        final var sessionFactory = sshServer.getSessionFactory();
        final var ioService = new SshIoService(sshServer,
            new DefaultChannelGroup("sshd-server-channels", channel.eventLoop()), sessionFactory);

        return new SshIoSession(ioService, sessionFactory, channel.localAddress());
    }
}