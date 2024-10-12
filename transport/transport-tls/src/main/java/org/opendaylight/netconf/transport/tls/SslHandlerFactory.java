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
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.CipherSuiteAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSAES128CCMSHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSAES128GCMSHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSAES256GCMSHA384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSCHACHA20POLY1305SHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSDHEPSKWITHAES128CCM;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSDHEPSKWITHAES128GCMSHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSDHEPSKWITHAES256CCM;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSDHEPSKWITHAES256GCMSHA384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSDHEPSKWITHCHACHA20POLY1305SHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSDHERSAWITHAES128CCM;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSDHERSAWITHAES128GCMSHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSDHERSAWITHAES256CCM;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSDHERSAWITHAES256GCMSHA384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSDHERSAWITHCHACHA20POLY1305SHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSECDHEECDSAWITHAES128GCMSHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSECDHEECDSAWITHAES256GCMSHA384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSECDHEECDSAWITHCHACHA20POLY1305SHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSECDHEPSKWITHAES128CCMSHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSECDHEPSKWITHAES128GCMSHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSECDHEPSKWITHAES256GCMSHA384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSECDHEPSKWITHCHACHA20POLY1305SHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSECDHERSAWITHAES128GCMSHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSECDHERSAWITHAES256GCMSHA384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev240208.TLSECDHERSAWITHCHACHA20POLY1305SHA256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev240208.InlineOrKeystoreAsymmetricKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev240208.InlineOrKeystoreEndEntityCertWithKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev240208.TlsClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev240208.tls.client.grouping.client.identity.auth.type.Certificate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev240208.tls.client.grouping.client.identity.auth.type.RawPublicKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev240208.HelloParamsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev240208.TlsVersionBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev240208.TlsServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev240208.tls.server.grouping.server.identity.auth.type.RawPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.InlineOrTruststoreCertsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.InlineOrTruststorePublicKeysGrouping;

/**
 * Extension interface for external service integration with TLS transport. Used to build {@link TLSClient} and
 * {@link TLSServer} instances.
 */
public abstract class SslHandlerFactory {
    private static final ImmutableMap<CipherSuiteAlgBase, String> CIPHER_SUITES =
        ImmutableMap.<CipherSuiteAlgBase, String>builder()
            .put(TLSAES128CCMSHA256.VALUE, "TLS_AES_128_CCM_SHA256")
            .put(TLSAES128GCMSHA256.VALUE, "TLS_AES_128_GCM_SHA256")
            .put(TLSAES256GCMSHA384.VALUE, "TLS_AES_256_GCM_SHA384")
            .put(TLSCHACHA20POLY1305SHA256.VALUE, "TLS_CHACHA20_POLY1305_SHA256")
            .put(TLSDHEPSKWITHAES128CCM.VALUE, "TLS_DHE_PSK_WITH_AES_128_CCM")
            .put(TLSDHEPSKWITHAES128GCMSHA256.VALUE, "TLS_DHE_PSK_WITH_AES_128_GCM_SHA256")
            .put(TLSDHEPSKWITHAES256CCM.VALUE, "TLS_DHE_PSK_WITH_AES_256_CCM")
            .put(TLSDHEPSKWITHAES256GCMSHA384.VALUE, "TLS_DHE_PSK_WITH_AES_256_GCM_SHA384")
            .put(TLSDHEPSKWITHCHACHA20POLY1305SHA256.VALUE, "TLS_DHE_PSK_WITH_CHACHA20_POLY1305_SHA256")
            .put(TLSDHERSAWITHAES128CCM.VALUE, "TLS_DHE_RSA_WITH_AES_128_CCM")
            .put(TLSDHERSAWITHAES128GCMSHA256.VALUE, "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256")
            .put(TLSDHERSAWITHAES256CCM.VALUE, "TLS_DHE_RSA_WITH_AES_256_CCM")
            .put(TLSDHERSAWITHAES256GCMSHA384.VALUE, "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384")
            .put(TLSDHERSAWITHCHACHA20POLY1305SHA256.VALUE, "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256")
            .put(TLSECDHEECDSAWITHAES128GCMSHA256.VALUE, "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
            .put(TLSECDHEECDSAWITHAES256GCMSHA384.VALUE, "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384")
            .put(TLSECDHEECDSAWITHCHACHA20POLY1305SHA256.VALUE, "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256")
            .put(TLSECDHEPSKWITHAES128CCMSHA256.VALUE, "TLS_ECDHE_PSK_WITH_AES_128_CCM_SHA256")
            .put(TLSECDHEPSKWITHAES128GCMSHA256.VALUE, "TLS_ECDHE_PSK_WITH_AES_128_GCM_SHA256")
            .put(TLSECDHEPSKWITHAES256GCMSHA384.VALUE, "TLS_ECDHE_PSK_WITH_AES_256_GCM_SHA384")
            .put(TLSECDHEPSKWITHCHACHA20POLY1305SHA256.VALUE, "TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256")
            .put(TLSECDHERSAWITHAES128GCMSHA256.VALUE, "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
            .put(TLSECDHERSAWITHAES256GCMSHA384.VALUE, "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")
            .put(TLSECDHERSAWITHCHACHA20POLY1305SHA256.VALUE, "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256")
            .build();

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
                instanceof org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev240208
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
                final var versions = tlsVersions.getTlsVersion();
                if (versions != null && !versions.isEmpty()) {
                    builder.protocols(createTlsStrings(versions));
                }
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

    private static ImmutableList<String> createCipherStrings(final List<CipherSuiteAlgBase> ciphers)
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
