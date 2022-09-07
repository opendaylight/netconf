/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev220718.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;

/**
 * A {@link TransportStack} acting as an SSH client.
 */
public final class SSHClient extends SSHTransportStack {
    private SSHClient(final TransportChannelListener listener) {
        super(listener);
    }

    public static @NonNull ListenableFuture<SSHClient> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams,
            final SshClientGrouping clientParams) throws UnsupportedConfigurationException {
        final var client = newClient(listener, clientParams);
        return transformUnderlay(client, TCPClient.connect(client.asListener(), bootstrap, connectParams));
    }

    public static @NonNull ListenableFuture<SSHClient> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams, final SshClientGrouping clientParams)
                throws UnsupportedConfigurationException {
        final var client = newClient(listener, clientParams);
        return transformUnderlay(client, TCPServer.listen(client.asListener(), bootstrap, listenParams));
    }

    @Override
    protected void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }

    private static SSHClient newClient(final TransportChannelListener listener, final SshClientGrouping clientParams)
            throws UnsupportedConfigurationException {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }
}