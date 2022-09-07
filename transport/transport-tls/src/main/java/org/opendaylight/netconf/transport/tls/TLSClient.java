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
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.netconf.transport.tcp.TCPTransportStack;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev220707.AsymmetricKeyPairGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev220707.EndEntityCertGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev220707.asymmetric.key.pair.grouping._private.key.type.CleartextPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev220524.local.or.keystore.end.entity.cert.with.key.grouping.local.or.keystore.Local;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev220718.TlsClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev220718.tls.client.grouping.client.identity.auth.type.Certificate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev220718.tls.client.grouping.client.identity.auth.type.RawPublicKey;

/**
 * A {@link TransportStack} acting as a TLS client.
 */
public final class TLSClient extends TLSTransportStack {
    private TLSClient(final TransportChannelListener listener, final TCPTransportStack tcpStack,
            final SslContext sslContext) {
        super(listener, tcpStack, sslContext);
    }

    public static @NonNull ListenableFuture<TLSClient> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams, final TlsClientGrouping clientParams)
                throws UnsupportedConfigurationException {
        final var sslContext = newSslContext(clientParams);
        return transform(TCPClient.connect(listener, bootstrap, connectParams), listener, sslContext);
    }

    public static @NonNull ListenableFuture<TLSClient> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams, final TlsClientGrouping clientParams)
                throws UnsupportedConfigurationException {
        final var sslContext = newSslContext(clientParams);
        return transform(TCPServer.listen(listener, bootstrap, listenParams), listener, sslContext);
    }

    private static @NonNull ListenableFuture<TLSClient> transform(
            final ListenableFuture<? extends TCPTransportStack> tcpFuture, final TransportChannelListener listener,
            final SslContext sslContext) {
        return Futures.transform(tcpFuture, tcpStack -> new TLSClient(listener, tcpStack, sslContext),
            MoreExecutors.directExecutor());
    }

    private static SslContext newSslContext(final TlsClientGrouping clientParams)
            throws UnsupportedConfigurationException {
        final var builder = SslContextBuilder.forClient();

        final var clientIdentity = clientParams.getClientIdentity();
        if (clientIdentity != null) {
            final var authType = clientIdentity.getAuthType();
            if (authType instanceof Certificate cert) {
                setKeyManager(builder, cert);
            } else if (authType instanceof RawPublicKey rawKey) {
                setKeyManager(builder, rawKey);
            } else if (authType != null) {
                throw new UnsupportedConfigurationException("Unsupported client authentication type " + authType);
            }
        }

        final var serverAuth = clientParams.getServerAuthentication();
        if (serverAuth != null) {
            // ca-certs or ee-certs or raw-public-keys
            // FIXME: implement this
        }

        return buildSslContext(builder, clientParams.getHelloParams());
    }

    private static void setKeyManager(final SslContextBuilder builder, final Certificate cert)
            throws UnsupportedConfigurationException {
        final var certificate = cert.getCertificate();
        if (certificate == null) {
            throw new UnsupportedConfigurationException("Missing certificate in " + cert);
        }

        final var localOrKeystore = certificate.getLocalOrKeystore();
        if (!(localOrKeystore instanceof Local local)) {
            throw new UnsupportedConfigurationException("Unsupported certificate " + localOrKeystore);
        }

        final var localDef = local.getLocalDefinition();
        if (localDef == null) {
            throw new UnsupportedConfigurationException("Missing local definition in " + local);
        }

        builder.keyManager(certStream(localDef), keyStream(localDef));
    }

    private static void setKeyManager(final SslContextBuilder builder, final RawPublicKey rawKey) {
        // FIXME: implement this
    }

    private static InputStream keyStream(final AsymmetricKeyPairGrouping key) throws UnsupportedConfigurationException {
        final var type = key.getPrivateKeyType();
        if (type instanceof CleartextPrivateKey clearText) {
            // FIXME: implement this
            return null;
        } else {
            throw new UnsupportedConfigurationException("Unsupported private key " + type);
        }
    }

    private static InputStream certStream(final EndEntityCertGrouping cert) {
        return new ByteArrayInputStream(cert.requireCertData().getValue());
    }
}
