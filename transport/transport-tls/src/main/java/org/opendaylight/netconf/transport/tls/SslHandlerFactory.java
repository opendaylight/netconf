/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static org.opendaylight.netconf.transport.tls.ConfigUtils.setAsymmetricKey;
import static org.opendaylight.netconf.transport.tls.ConfigUtils.setEndEntityCertificateWithKey;
import static org.opendaylight.netconf.transport.tls.ConfigUtils.setX509Certificates;
import static org.opendaylight.netconf.transport.tls.KeyStoreUtils.buildKeyManagerFactory;
import static org.opendaylight.netconf.transport.tls.KeyStoreUtils.buildTrustManagerFactory;
import static org.opendaylight.netconf.transport.tls.KeyStoreUtils.newKeyStore;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.netty.channel.Channel;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import java.net.SocketAddress;
import java.security.KeyStore;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tls.impl.IetfTlsCommonFeatureProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev241016.TlsCipherSuiteAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.InlineOrKeystoreAsymmetricKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.InlineOrKeystoreEndEntityCertWithKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev241010.TlsClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev241010.tls.client.grouping.client.identity.auth.type.Certificate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev241010.tls.client.grouping.client.identity.auth.type.RawPublicKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev241010.HelloParamsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev241010.Tls12$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev241010.Tls13$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev241010.TlsVersionBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev241010.hello.params.grouping.TlsVersions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010.TlsServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010.tls.server.grouping.server.identity.auth.type.RawPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.InlineOrTruststoreCertsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.InlineOrTruststorePublicKeysGrouping;

/**
 * Extension interface for external service integration with TLS transport. Used to build {@link TLSClient} and
 * {@link TLSServer} instances.
 */
public abstract class SslHandlerFactory {
    // FIXME: rework this lookup to validate known strings -- the enum contains the string anyway
    private static final ImmutableMap<TlsCipherSuiteAlgorithm, String> CIPHER_SUITES = index(
        TlsCipherSuiteAlgorithm.TLSAES128CCMSHA256,
        TlsCipherSuiteAlgorithm.TLSAES256GCMSHA384,
        TlsCipherSuiteAlgorithm.TLSCHACHA20POLY1305SHA256,
        TlsCipherSuiteAlgorithm.TLSDHEPSKWITHAES128CCM,
        TlsCipherSuiteAlgorithm.TLSDHEPSKWITHAES128GCMSHA256,
        TlsCipherSuiteAlgorithm.TLSDHEPSKWITHAES256CCM,
        TlsCipherSuiteAlgorithm.TLSDHEPSKWITHAES256GCMSHA384,
        TlsCipherSuiteAlgorithm.TLSDHEPSKWITHCHACHA20POLY1305SHA256,
        TlsCipherSuiteAlgorithm.TLSDHERSAWITHAES128CCM,
        TlsCipherSuiteAlgorithm.TLSDHERSAWITHAES128GCMSHA256,
        TlsCipherSuiteAlgorithm.TLSDHERSAWITHAES256CCM,
        TlsCipherSuiteAlgorithm.TLSDHERSAWITHAES256GCMSHA384,
        TlsCipherSuiteAlgorithm.TLSDHERSAWITHCHACHA20POLY1305SHA256,
        TlsCipherSuiteAlgorithm.TLSECDHEECDSAWITHAES128GCMSHA256,
        TlsCipherSuiteAlgorithm.TLSECDHEECDSAWITHAES256GCMSHA384,
        TlsCipherSuiteAlgorithm.TLSECDHEECDSAWITHCHACHA20POLY1305SHA256,
        TlsCipherSuiteAlgorithm.TLSECDHEPSKWITHAES128CCMSHA256,
        TlsCipherSuiteAlgorithm.TLSECDHEPSKWITHAES128GCMSHA256,
        TlsCipherSuiteAlgorithm.TLSECDHEPSKWITHAES256GCMSHA384,
        TlsCipherSuiteAlgorithm.TLSECDHEPSKWITHCHACHA20POLY1305SHA256,
        TlsCipherSuiteAlgorithm.TLSECDHERSAWITHAES128GCMSHA256,
        TlsCipherSuiteAlgorithm.TLSECDHERSAWITHAES256GCMSHA384,
        TlsCipherSuiteAlgorithm.TLSECDHERSAWITHCHACHA20POLY1305SHA256);

    private static ImmutableMap<TlsCipherSuiteAlgorithm, String> index(final TlsCipherSuiteAlgorithm... algs) {
        final var builder = ImmutableMap.<TlsCipherSuiteAlgorithm, String>builderWithExpectedSize(algs.length);
        for (var alg : algs) {
            builder.put(alg, alg.getName());
        }
        return builder.build();
    }

    /**
     * Builds {@link SslHandler} instance for given {@link Channel}.
     *
     * @param channel channel
     * @return A {@link SslHandler}, or {@code null} if the connection should be rejected
     */
    public final @Nullable SslHandler createSslHandler(final @NonNull Channel channel) {
        final var sslContext = getSslContext(channel.remoteAddress());
        return sslContext == null ? null : sslContext.newHandler(channel.alloc());
    }

    protected abstract @Nullable SslContext getSslContext(SocketAddress remoteAddress);

    protected static final @NonNull SslContext createSslContext(final @NonNull TlsClientGrouping clientParams)
            throws UnsupportedConfigurationException {
        return createSslContext(clientParams, null);
    }

    protected static final @NonNull SslContext createSslContext(final @NonNull TlsClientGrouping clientParams,
            final @Nullable ApplicationProtocolConfig apn) throws UnsupportedConfigurationException {
        final var builder = SslContextBuilder.forClient().applicationProtocolConfig(apn);

        final var clientIdentity = clientParams.getClientIdentity();
        if (clientIdentity != null) {
            final var authType = clientIdentity.getAuthType();
            if (authType instanceof Certificate cert) {
                // if-feature "client-ident-x509-cert"
                final var certificate = cert.getCertificate();
                if (certificate == null) {
                    throw new UnsupportedConfigurationException("Missing certificate in " + cert);
                }
                builder.keyManager(newKeyManager(certificate));
            } else if (authType instanceof RawPublicKey rawKey) {
                // if-feature "client-ident-raw-public-key"
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
            // CA && EE X509 certificates : if-feature "server-ident-x509-cert"
            // Raw public key : if-feature "server-ident-raw-public-key"
            final var trustManager = newTrustManager(serverAuth.getCaCerts(), serverAuth.getEeCerts(),
                    serverAuth.getRawPublicKeys());
            if (trustManager == null) {
                throw new UnsupportedOperationException("No server authentication methods in " + serverAuth);
            }
            builder.trustManager(trustManager);
        }

        return buildSslContext(builder, clientParams.getHelloParams());
    }

    protected static final @NonNull SslContext createSslContext(final @NonNull TlsServerGrouping serverParams)
            throws UnsupportedConfigurationException {
        return createSslContext(serverParams, null);
    }

    protected static final @NonNull SslContext createSslContext(final @NonNull TlsServerGrouping serverParams,
            final @Nullable ApplicationProtocolConfig apn) throws UnsupportedConfigurationException {
        final var serverIdentity = serverParams.getServerIdentity();
        if (serverIdentity == null) {
            throw new UnsupportedConfigurationException("Missing server identity");
        }
        final SslContextBuilder builder;
        final var authType = serverIdentity.getAuthType();
        if (authType
                instanceof org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010
                           .tls.server.grouping.server.identity.auth.type.Certificate cert) {
            // if-feature "server-ident-x509-cert"
            final var certificate = cert.getCertificate();
            if (certificate == null) {
                throw new UnsupportedConfigurationException("Missing certificate in " + cert);
            }
            builder = SslContextBuilder.forServer(newKeyManager(certificate));
        } else if (authType instanceof RawPrivateKey rawKey) {
            // if-feature "server-ident-raw-public-key"
            final var rawPrivateKey = rawKey.getRawPrivateKey();
            if (rawPrivateKey == null) {
                throw new UnsupportedConfigurationException("Missing key in " + rawKey);
            }
            builder = SslContextBuilder.forServer(newKeyManager(rawPrivateKey));
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

        return buildSslContext(builder.applicationProtocolConfig(apn), serverParams.getHelloParams());
    }

    // FIXME: should be TrustManagerBuilder
    private static @Nullable TrustManagerFactory newTrustManager(
            final @Nullable InlineOrTruststoreCertsGrouping caCerts,
            final @Nullable InlineOrTruststoreCertsGrouping eeCerts,
            final @Nullable InlineOrTruststorePublicKeysGrouping publicKeys) throws UnsupportedConfigurationException {

        if (publicKeys != null) {
            // FIXME: implement this and advertize server-auth-raw-public-key from IetfTlsClientFeatureProvider
            throw new UnsupportedConfigurationException("Public key authentication not implemented");
        }
        if (caCerts != null || eeCerts != null) {
            // X.509 certificates
            final KeyStore keyStore = newKeyStore();
            setX509Certificates(keyStore, caCerts, eeCerts);
            return buildTrustManagerFactory(keyStore);
        }
        return null;
    }

    private static KeyManagerFactory newKeyManager(
            final @NonNull InlineOrKeystoreEndEntityCertWithKeyGrouping endEntityCert)
            throws UnsupportedConfigurationException {
        final var keyStore = newKeyStore();
        setEndEntityCertificateWithKey(keyStore, endEntityCert);
        return buildKeyManagerFactory(keyStore);
    }

    private static KeyManagerFactory newKeyManager(final @NonNull InlineOrKeystoreAsymmetricKeyGrouping rawPrivateKey)
            throws UnsupportedConfigurationException {
        final var keyStore = newKeyStore();
        setAsymmetricKey(keyStore, rawPrivateKey);
        return buildKeyManagerFactory(keyStore);
    }

    private static @NonNull SslContext buildSslContext(final SslContextBuilder builder,
            final HelloParamsGrouping helloParams) throws UnsupportedConfigurationException {
        if (helloParams != null) {
            final var tlsVersions = helloParams.getTlsVersions();
            if (tlsVersions != null) {
                builder.protocols(createTlsStrings(createTlsVersions(tlsVersions)));
            }
            final var cipherSuites = helloParams.getCipherSuites();
            if (cipherSuites != null) {
                final var ciphers = cipherSuites.getCipherSuite();
                if (ciphers != null && !ciphers.isEmpty()) {
                    builder.ciphers(createCipherStrings(ciphers));
                }
            }
        }
        try {
            return builder.build();
        } catch (SSLException e) {
            throw new UnsupportedConfigurationException("Cannot instantiate TLS context", e);
        }
    }

    private static List<TlsVersionBase> createTlsVersions(final TlsVersions versions)
            throws UnsupportedConfigurationException {
        final var min = Objects.requireNonNullElse(versions.getMin(), Tls12$I.VALUE);
        final var max = Objects.requireNonNullElse(versions.getMax(), Tls13$I.VALUE);

        return switch (min) {
            case Tls12$I min12 ->
                switch (max) {
                    case Tls12$I max12 -> List.of(max12);
                    case Tls13$I max13 -> List.of(min12, max13);
                    default -> throw new UnsupportedConfigurationException("Unsupported TLS version " + min);
                };
            case Tls13$I min13 ->
                switch (max) {
                    case Tls12$I max12 -> throw new UnsupportedConfigurationException(
                        "Invalid TLS version range in " + versions);
                    case Tls13$I max13 -> List.of(Tls13$I.VALUE);
                    default -> throw new UnsupportedConfigurationException("Unsupported TLS version " + min);
                };
            default -> throw new UnsupportedConfigurationException("Unsupported TLS version " + min);
        };
    }

    private static String[] createTlsStrings(final List<TlsVersionBase> versions)
            throws UnsupportedConfigurationException {
        // FIXME: cache these
        final var ret = new String[versions.size()];
        int idx = 0;
        for (var version : versions) {
            final var str = IetfTlsCommonFeatureProvider.algorithmNameOf(version);
            if (str == null) {
                throw new UnsupportedConfigurationException("Unhandled TLS version " + version);
            }
            ret[idx++] = str;
        }
        return ret;
    }

    private static ImmutableList<String> createCipherStrings(final List<TlsCipherSuiteAlgorithm> ciphers)
            throws UnsupportedConfigurationException {
        // FIXME: cache these
        final var builder = ImmutableList.<String>builderWithExpectedSize(ciphers.size());
        for (var cipher : ciphers) {
            final var str = CIPHER_SUITES.get(cipher);
            if (str == null) {
                throw new UnsupportedConfigurationException("Unhandled cipher suite " + cipher);
            }
            builder.add(str);
        }
        return builder.build();
    }
}
