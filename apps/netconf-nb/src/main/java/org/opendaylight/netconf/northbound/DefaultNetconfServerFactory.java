/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Map;
import org.opendaylight.netconf.server.NetconfServerFactoryImpl;
import org.opendaylight.netconf.server.ServerChannelInitializer;
import org.opendaylight.netconf.server.api.NetconfServerFactory;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHServer;
import org.opendaylight.netconf.transport.ssh.ServerFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(factory = DefaultNetconfServerFactory.FACTORY_NAME, service = NetconfServerFactory.class)
public final class DefaultNetconfServerFactory implements NetconfServerFactory {
    static final String OSGI_TYPE = "netconf-server-factory";
    static final String FACTORY_NAME = "org.opendaylight.netconf.northbound.DefaultNetconfServerFactory";

    private static final String INITIALIZER_PROP = ".initializer";

    private final NetconfServerFactory delegate;

    @Activate
    public DefaultNetconfServerFactory(@Reference final NettyFootprint netty, final Map<String, ?> properties) {
        delegate = new NetconfServerFactoryImpl(
            OSGiNetconfServer.extractProp(properties, INITIALIZER_PROP, ServerChannelInitializer.class),
            netty.bossGroup(), netty.workerGroup());
    }

    static Map<String, ?> props(final ServerChannelInitializer initializer) {
        return Map.of(
            "type", OSGI_TYPE,
            INITIALIZER_PROP, requireNonNull(initializer));
    }

    @Override
    public ListenableFuture<TCPServer> createTcpServer(final TcpServerGrouping params)
            throws UnsupportedConfigurationException {
        return delegate.createTcpServer(params);
    }

    @Override
    public ListenableFuture<SSHServer> createSshServer(final TcpServerGrouping tcpParams,
            final SshServerGrouping sshParams) throws UnsupportedConfigurationException {
        return delegate.createSshServer(tcpParams, sshParams);
    }

    @Override
    public ListenableFuture<SSHServer> createSshServer(final TcpServerGrouping tcpParams,
            final SshServerGrouping sshParams, final ServerFactoryManagerConfigurator configurator)
            throws UnsupportedConfigurationException {
        return delegate.createSshServer(tcpParams, sshParams, configurator);
    }
}
