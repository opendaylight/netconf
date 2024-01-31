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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.CipherSuiteAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsAes128CcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsAes128GcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsAes256GcmSha384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsChacha20Poly1305Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDhePskWithAes128Ccm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDhePskWithAes128GcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDhePskWithAes256Ccm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDhePskWithAes256GcmSha384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDhePskWithChacha20Poly1305Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDheRsaWithAes128Ccm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDheRsaWithAes128GcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDheRsaWithAes256Ccm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDheRsaWithAes256GcmSha384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDheRsaWithChacha20Poly1305Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdheEcdsaWithAes128GcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdheEcdsaWithAes256GcmSha384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdheEcdsaWithChacha20Poly1305Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdhePskWithAes128CcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdhePskWithAes128GcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdhePskWithAes256GcmSha384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdhePskWithChacha20Poly1305Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdheRsaWithAes128GcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdheRsaWithAes256GcmSha384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdheRsaWithChacha20Poly1305Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev231228.InlineOrKeystoreAsymmetricKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev231228.InlineOrKeystoreEndEntityCertWithKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev231228.TlsClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev231228.tls.client.grouping.client.identity.auth.type.Certificate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev231228.tls.client.grouping.client.identity.auth.type.RawPublicKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev231228.HelloParamsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev231228.TlsVersionBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev231228.TlsServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev231228.tls.server.grouping.server.identity.auth.type.RawPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev231228.InlineOrTruststoreCertsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev231228.InlineOrTruststorePublicKeysGrouping;

/**
 * Extension interface for external service integration with TLS transport. Used to build {@link TLSClient} and
 * {@link TLSServer} instances.
 */
public abstract class SslHandlerFactory {
    private static final ImmutableMap<CipherSuiteAlgBase, String> CIPHER_SUITES =
        ImmutableMap.<CipherSuiteAlgBase, String>builder()
            .put(TlsAes128CcmSha256.VALUE, "TLS_AES_128_CCM_SHA256")
            .put(TlsAes128GcmSha256.VALUE, "TLS_AES_128_GCM_SHA256")
            .put(TlsAes256GcmSha384.VALUE, "TLS_AES_256_GCM_SHA384")
            .put(TlsChacha20Poly1305Sha256.VALUE, "TLS_CHACHA20_POLY1305_SHA256")
            .put(TlsDhePskWithAes128Ccm.VALUE, "TLS_DHE_PSK_WITH_AES_128_CCM")
            .put(TlsDhePskWithAes128GcmSha256.VALUE, "TLS_DHE_PSK_WITH_AES_128_GCM_SHA256")
            .put(TlsDhePskWithAes256Ccm.VALUE, "TLS_DHE_PSK_WITH_AES_256_CCM")
            .put(TlsDhePskWithAes256GcmSha384.VALUE, "TLS_DHE_PSK_WITH_AES_256_GCM_SHA384")
            .put(TlsDhePskWithChacha20Poly1305Sha256.VALUE, "TLS_DHE_PSK_WITH_CHACHA20_POLY1305_SHA256")
            .put(TlsDheRsaWithAes128Ccm.VALUE, "TLS_DHE_RSA_WITH_AES_128_CCM")
            .put(TlsDheRsaWithAes128GcmSha256.VALUE, "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256")
            .put(TlsDheRsaWithAes256Ccm.VALUE, "TLS_DHE_RSA_WITH_AES_256_CCM")
            .put(TlsDheRsaWithAes256GcmSha384.VALUE, "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384")
            .put(TlsDheRsaWithChacha20Poly1305Sha256.VALUE, "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256")
            .put(TlsEcdheEcdsaWithAes128GcmSha256.VALUE, "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
            .put(TlsEcdheEcdsaWithAes256GcmSha384.VALUE, "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384")
            .put(TlsEcdheEcdsaWithChacha20Poly1305Sha256.VALUE, "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256")
            .put(TlsEcdhePskWithAes128CcmSha256.VALUE, "TLS_ECDHE_PSK_WITH_AES_128_CCM_SHA256")
            .put(TlsEcdhePskWithAes128GcmSha256.VALUE, "TLS_ECDHE_PSK_WITH_AES_128_GCM_SHA256")
            .put(TlsEcdhePskWithAes256GcmSha384.VALUE, "TLS_ECDHE_PSK_WITH_AES_256_GCM_SHA384")
            .put(TlsEcdhePskWithChacha20Poly1305Sha256.VALUE, "TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256")
            .put(TlsEcdheRsaWithAes128GcmSha256.VALUE, "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
            .put(TlsEcdheRsaWithAes256GcmSha384.VALUE, "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")
            .put(TlsEcdheRsaWithChacha20Poly1305Sha256.VALUE, "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256")
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
        final var builder = SslContextBuilder.forClient();

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
        final var serverIdentity = serverParams.getServerIdentity();
        if (serverIdentity == null) {
            throw new UnsupportedConfigurationException("Missing server identity");
        }
        final SslContextBuilder builder;
        final var authType = serverIdentity.getAuthType();
        if (authType
                instanceof org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev231228
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

        return buildSslContext(builder, serverParams.getHelloParams());
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
