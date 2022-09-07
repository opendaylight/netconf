/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.ServerBootstrap;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev220718.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;

/**
 * A {@link TransportStack} acting as an SSH server.
 */
public final class SSHServer extends SSHTransportStack {
    private SSHServer(final TransportChannelListener listener) {
        super(listener);
    }

    public static @NonNull ListenableFuture<SSHServer> connect(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpClientGrouping connectParams,
            final SshServerGrouping serverParams) throws UnsupportedConfigurationException {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }

    public static @NonNull ListenableFuture<SSHServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams, final SshServerGrouping serverParams)
                throws UnsupportedConfigurationException {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }
}