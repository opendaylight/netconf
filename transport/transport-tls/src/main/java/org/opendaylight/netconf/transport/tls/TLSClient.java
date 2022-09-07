/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.netconf.transport.tcp.TCPTransportStack;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev220718.TlsClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev220718.tls.client.grouping.ServerAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev220718.tls.client.grouping.client.identity.auth.type.Certificate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev220718.tls.client.grouping.client.identity.auth.type.RawPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TransportStack} acting as a TLS client.
 */
public final class TLSClient extends TLSTransportStack {
    private static final Logger LOG = LoggerFactory.getLogger(TLSClient.class);

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
                final var certificate = cert.getCertificate();
                if (certificate == null) {
                    throw new UnsupportedConfigurationException("Missing certificate in " + cert);
                }
                builder.keyManager(newKeyManager(certificate));
            } else if (authType instanceof RawPublicKey rawKey) {
                final var rawPrivateKey = rawKey.getRawPrivateKey();
                if (rawPrivateKey == null) {
                    throw new UnsupportedConfigurationException("Missing key in " + rawKey);
                }
                builder.keyManager(newKeyManager(rawPrivateKey));
            } else if (authType != null) {
                throw new UnsupportedConfigurationException("Unsupported client authentication type " + authType);
            }
        }

        final var serverAuth = clientParams.getServerAuthentication();
        if (serverAuth != null) {
            builder.trustManager(newTrustManager(serverAuth));
        }

        return buildSslContext(builder, clientParams.getHelloParams());
    }

    private static @NonNull TrustManager newTrustManager(final ServerAuthentication serverAuth)
            throws UnsupportedConfigurationException {
        KeyStore keyStore = null;

        final var caCerts = serverAuth.getCaCerts();
        if (caCerts != null) {
            keyStore = loadKeyStore(null, "ca-", caCerts);
        }
        final var eeCerts = serverAuth.getEeCerts();
        if (eeCerts != null) {
            keyStore = loadKeyStore(keyStore, "ee-", eeCerts);
        }

        final var builder = ImmutableList.<X509TrustManager>builder();
        if (keyStore != null) {
            final TrustManagerFactory tmf;
            try {
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            } catch (NoSuchAlgorithmException e) {
                throw new UnsupportedConfigurationException("Cannot instantiate trust manager", e);
            }
            try {
                tmf.init(keyStore);
            } catch (KeyStoreException e) {
                throw new UnsupportedConfigurationException("Cannot initialize trust manager", e);
            }

            final var managers = tmf.getTrustManagers();
            if (managers != null) {
                for (var manager : managers) {
                    if (manager instanceof X509TrustManager x509) {
                        builder.add(x509);
                    } else {
                        LOG.debug("Ignoring trust manager {}", manager);
                    }
                }
            }
        }

        final var rawPublicKeys = serverAuth.getRawPublicKeys();
        if (rawPublicKeys != null) {
            // FIXME: implement this and advertize server-auth-raw-public-key from IetfTlsClientFeatureProvider
            throw new UnsupportedConfigurationException("Public key authentication not implemented");
        }

        final var managers = builder.build();
        return switch (managers.size()) {
            case 0 -> throw new UnsupportedOperationException("No server authentication methods in " + serverAuth);
            case 1 -> managers.get(0);
            default -> new CompositeX509TrustManager(managers);
        };
    }
}
