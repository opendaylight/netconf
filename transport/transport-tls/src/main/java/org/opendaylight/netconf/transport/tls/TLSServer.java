/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static java.util.Optional.ofNullable;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev221212.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev221212.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev221212.TlsServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev221212.tls.server.grouping.server.identity.auth.type.Certificate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev221212.tls.server.grouping.server.identity.auth.type.RawPrivateKey;

/**
 * A {@link TransportStack} acting as a TLS server.
 */
public final class TLSServer extends TLSTransportStack {
    private TLSServer(final TransportChannelListener listener, final SslContext sslContext) {
        super(listener, sslContext);
    }

    public static @NonNull ListenableFuture<TLSServer> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams, final TlsServerGrouping serverParams)
            throws UnsupportedConfigurationException {
        final var server = newServer(listener, serverParams);
        return transformUnderlay(server, TCPClient.connect(server.asListener(), bootstrap, connectParams));
    }

    public static @NonNull ListenableFuture<TLSServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams, final TlsServerGrouping serverParams)
            throws UnsupportedConfigurationException {
        final var server = newServer(listener, serverParams);
        return transformUnderlay(server, TCPServer.listen(server.asListener(), bootstrap, listenParams));
    }

    private static TLSServer newServer(final TransportChannelListener listener, final TlsServerGrouping serverParams)
            throws UnsupportedConfigurationException {
        final var serverIdentity = serverParams.getServerIdentity();
        if (serverIdentity == null) {
            throw new UnsupportedConfigurationException("Missing server identity");
        }

        final SslContextBuilder builder;
        final var authType = serverIdentity.getAuthType();
        if (authType instanceof Certificate cert) {
            // if-feature "server-ident-x509-cert";
            builder = newSslContextBuilder(cert);
        } else if (authType instanceof RawPrivateKey rawKey) {
            // if-feature "server-ident-raw-public-key";
            builder = newSslContextBuilder(rawKey);
        } else if (authType != null) {
            throw new UnsupportedConfigurationException("Unsupported server authentication type " + authType);
        } else {
            throw new UnsupportedConfigurationException("Missing server authentication type");
        }

        final var clientAuth = serverParams.getClientAuthentication();
        if (clientAuth != null) {
            // CA && EE Certs : if-feature "client-ident-x509-cert"
            // Raw public keys : if-feature "client-ident-raw-public-key"
            final var trustManager = newTrustManager(clientAuth.getCaCerts(), clientAuth.getEeCerts(),
                    clientAuth.getRawPublicKeys());
            if (trustManager == null) {
                throw new UnsupportedOperationException("No client authentication methods in " + clientAuth);
            }
            builder.clientAuth(ClientAuth.REQUIRE).trustManager(trustManager);
        } else {
            builder.clientAuth(ClientAuth.NONE);
        }
        return new TLSServer(listener, buildSslContext(builder, serverParams.getHelloParams()));
    }

    private static SslContextBuilder newSslContextBuilder(final Certificate cert)
            throws UnsupportedConfigurationException {
        final var certificate = ofNullable(cert.getCertificate())
                .orElseThrow(() -> new UnsupportedConfigurationException("Missing certificate in " + cert));
        return SslContextBuilder.forServer(newKeyManager(certificate));
    }

    private static SslContextBuilder newSslContextBuilder(final RawPrivateKey rawKey)
            throws UnsupportedConfigurationException {
        final var rawPrivateKey = ofNullable(rawKey.getRawPrivateKey())
                .orElseThrow(() -> new UnsupportedConfigurationException("Missing key in " + rawKey));
        return SslContextBuilder.forServer(newKeyManager(rawPrivateKey));
    }
}
