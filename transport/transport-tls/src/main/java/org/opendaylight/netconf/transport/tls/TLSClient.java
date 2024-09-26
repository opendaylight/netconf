/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.handler.ssl.SslContext;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev240208.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev240208.TcpServerGrouping;

/**
 * A {@link TransportStack} acting as a TLS client.
 */
public final class TLSClient extends TLSTransportStack {
    private TLSClient(final TransportChannelListener<? super TLSTransportChannel> listener,
            final SslContext sslContext) {
        super(listener, sslContext);
    }

    private TLSClient(final TransportChannelListener<? super TLSTransportChannel> listener,
            final SslHandlerFactory factory) {
        super(listener, factory);
    }

    public static @NonNull ListenableFuture<TLSClient> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams, final SslHandlerFactory factory)
                throws UnsupportedConfigurationException {
        final var client = new TLSClient(listener, factory);
        return transformUnderlay(client, TCPClient.connect(client.asListener(), bootstrap, connectParams));
    }

    public static @NonNull ListenableFuture<TLSClient> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams, final SslHandlerFactory factory)
            throws UnsupportedConfigurationException {
        final var client = new TLSClient(listener, factory);
        return transformUnderlay(client, TCPServer.listen(client.asListener(), bootstrap, listenParams));
    }
}
