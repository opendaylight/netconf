/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import javax.net.ssl.KeyManagerFactory;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.netconf.transport.tcp.TCPTransportStack;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev220718.TlsServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev220718.tls.server.grouping.server.identity.auth.type.Certificate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev220718.tls.server.grouping.server.identity.auth.type.RawPrivateKey;

/**
 * A {@link TransportStack} acting as a TLS server.
 */
public final class TLSServer extends TLSTransportStack {
    private TLSServer(final TransportChannelListener listener, final TCPTransportStack tcpStack,
            final SslContext sslContext) {
        super(listener, tcpStack, sslContext);
    }

    public static @NonNull ListenableFuture<TLSServer> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams, final TlsServerGrouping serverParams)
                throws UnsupportedConfigurationException {
        final var sslContext = newSslContext(serverParams);
        return transform(TCPClient.connect(listener, bootstrap, connectParams), listener, sslContext);
    }

    public static @NonNull ListenableFuture<TLSServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams, final TlsServerGrouping serverParams)
                throws UnsupportedConfigurationException {
        final var sslContext = newSslContext(serverParams);
        return transform(TCPServer.listen(listener, bootstrap, listenParams), listener, sslContext);
    }

    private static @NonNull ListenableFuture<TLSServer> transform(
            final ListenableFuture<? extends TCPTransportStack> tcpFuture, final TransportChannelListener listener,
            final SslContext sslContext) {
        return Futures.transform(tcpFuture, tcpStack -> new TLSServer(listener, tcpStack, sslContext),
            MoreExecutors.directExecutor());
    }

    private static SslContext newSslContext(final TlsServerGrouping serverParams)
            throws UnsupportedConfigurationException {
        final var serverIdentity = serverParams.getServerIdentity();
        if (serverIdentity == null) {
            throw new UnsupportedConfigurationException("Missing server identity");
        }

        final SslContextBuilder builder;
        final var authType = serverIdentity.getAuthType();
        if (authType instanceof Certificate cert) {
            builder = newSslContextBuilder(cert);
        } else if (authType instanceof RawPrivateKey rawKey) {
            builder = newSslContextBuilder(rawKey);
        } else if (authType != null) {
            throw new UnsupportedConfigurationException("Unsupported server authentication type " + authType);
        } else {
            throw new UnsupportedConfigurationException("Missing server authentication type");
        }

        final var clientAuth = serverParams.getClientAuthentication();
        if (clientAuth != null) {
            // FIXME: implement this
            builder.clientAuth(ClientAuth.REQUIRE);
        } else {
            builder.clientAuth(ClientAuth.NONE);
        }

        return buildSslContext(builder, serverParams.getHelloParams());
    }

    private static SslContextBuilder newSslContextBuilder(final Certificate cert) {
        final var builder = SslContextBuilder.forServer((KeyManagerFactory) null);
        // FIXME: implement this
        return builder;
    }

    private static SslContextBuilder newSslContextBuilder(final RawPrivateKey rawKey) {
        final var builder = SslContextBuilder.forServer((KeyManagerFactory) null);
        // FIXME: implement this
        return builder;
    }
}
