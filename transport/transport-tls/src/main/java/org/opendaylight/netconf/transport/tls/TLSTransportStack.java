/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.AbstractTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPTransportStack;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev220718.TlsClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev220718.TlsServerGrouping;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * A {@link TransportStack} based on {@code TLS} connections.
 */
public abstract sealed class TLSTransportStack extends AbstractTransportStack {
    private static final class Client extends TLSTransportStack {
        Client(final TCPTransportStack tcpStack) {
            super(tcpStack);
        }
    }

    private static final class Server extends TLSTransportStack {
        Server(final TCPTransportStack tcpStack) {
            super(tcpStack);
        }
    }

    private final TCPTransportStack tcpStack;

    private TLSTransportStack(final TCPTransportStack tcpStack) {
        this.tcpStack = requireNonNull(tcpStack);
    }

    public static @NonNull ListenableFuture<TransportStack> connectClient(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams, final TlsClientGrouping clientParams)
                throws UnsupportedConfigurationException {
        // TODO Auto-generated method stub
        return null;
    }

    public static @NonNull ListenableFuture<TransportStack> listenClient(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams, final TlsClientGrouping clientParams)
                throws UnsupportedConfigurationException {
        // TODO Auto-generated method stub
        return null;
    }

    public static @NonNull ListenableFuture<TransportStack> connectServer(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams, final TlsServerGrouping serverParams)
                throws UnsupportedConfigurationException {
        // TODO Auto-generated method stub
        return null;
    }

    public static @NonNull ListenableFuture<TransportStack> listenServer(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams, final TlsServerGrouping serverParams)
            throws UnsupportedConfigurationException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected final ListenableFuture<Empty> startShutdown() {
        return tcpStack.shutdown();
    }
}
