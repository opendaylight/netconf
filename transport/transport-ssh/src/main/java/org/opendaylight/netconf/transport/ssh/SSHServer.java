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
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactoryFactory;
import org.opendaylight.netconf.shaded.sshd.server.ServerFactoryManager;
import org.opendaylight.netconf.shaded.sshd.server.session.SessionFactory;
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
    private final ServerFactoryManager serverFactoryManager;
    private final SessionFactory serverSessionFactory;

    private SSHServer(final TransportChannelListener listener, final ServerFactoryManager serverFactoryManager) {
        super(listener);
        this.serverFactoryManager = requireNonNull(serverFactoryManager);
        this.serverFactoryManager.addSessionListener(new UserAuthSessionListener(sessionAuthHandlers, sessions));
        serverSessionFactory = new SessionFactory(serverFactoryManager);
        ioService = new SshIoService(this.serverFactoryManager,
                new DefaultChannelGroup("sshd-server-channels", GlobalEventExecutor.INSTANCE),
                serverSessionFactory);
    }

    static SSHServer of(final NettyIoServiceFactoryFactory ioServiceFactory, final EventLoopGroup group,
            final TransportChannelListener listener, final SshServerGrouping serverParams,
            final ServerFactoryManagerConfigurator configurator) throws UnsupportedConfigurationException {
        return new SSHServer(listener, new TransportSshServer.Builder(ioServiceFactory, group)
            .serverParams(serverParams)
            .configurator(configurator)
            .buildChecked());
    }

    @Override
    IoHandler getSessionFactory() {
        return serverSessionFactory;
    }

    @NonNull ListenableFuture<SSHServer> connect(final Bootstrap bootstrap, final TcpClientGrouping connectParams)
            throws UnsupportedConfigurationException {
        return transformUnderlay(this, TCPClient.connect(asListener(), bootstrap, connectParams));
    }

    @NonNull ListenableFuture<SSHServer> listen(final ServerBootstrap bootstrap, final TcpServerGrouping connectParams)
            throws UnsupportedConfigurationException {
        return transformUnderlay(this, TCPServer.listen(asListener(), bootstrap, connectParams));
    }
}