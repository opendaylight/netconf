/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.netconf.api.TransportConstants;
import org.opendaylight.netconf.server.api.NetconfServerFactory;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHServer;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.ssh.ServerFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;

public final class NetconfServerFactoryImpl implements NetconfServerFactory {
    private final SSHTransportStackFactory factory;
    private final ServerChannelInitializer channelInitializer;

    public NetconfServerFactoryImpl(final ServerChannelInitializer channelInitializer,
            final SSHTransportStackFactory factory) {
        this.factory = requireNonNull(factory);
        this.channelInitializer = requireNonNull(channelInitializer);
    }

    @Override
    public ListenableFuture<TCPServer> createTcpServer(final TcpServerGrouping params)
            throws UnsupportedConfigurationException {
        return TCPServer.listen(new ServerTransportInitializer(channelInitializer), factory.newServerBootstrap(),
            params);
    }

    @Override
    public ListenableFuture<SSHServer> createSshServer(final TcpServerGrouping tcpParams,
            final SshServerGrouping sshParams, final ServerFactoryManagerConfigurator configurator)
                throws UnsupportedConfigurationException {
        return factory.listenServer(TransportConstants.SSH_SUBSYSTEM,
            new ServerTransportInitializer(channelInitializer), tcpParams, sshParams, configurator);
    }
}
