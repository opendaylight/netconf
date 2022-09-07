/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.util.List;
import java.util.Set;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.AbstractOverlayTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.AsymmetricKeyPairGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.TrustAnchorCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.asymmetric.key.pair.grouping._private.key.type.CleartextPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.LocalOrKeystoreAsymmetricKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.LocalOrKeystoreEndEntityCertWithKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.local.or.keystore.end.entity.cert.with.key.grouping.local.or.keystore.Local;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev221212.HelloParamsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev221212.TlsVersionBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.LocalOrTruststoreCertsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.LocalOrTruststorePublicKeysGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for TLS TransportStacks.
 */
public abstract sealed class TLSTransportStack extends AbstractOverlayTransportStack<TLSTransportChannel>
        permits TLSClient, TLSServer {
    private static final Logger LOG = LoggerFactory.getLogger(TLSTransportStack.class);
    private static final char[] EMPTY_CHARS = new char[0];
    private static final ImmutableMap<CipherSuiteAlgBase, String> CIPHER_SUITES =
        ImmutableMap.<CipherSuiteAlgBase, String>builder()
            .put(TlsAes128CcmSha256.VALUE,                      "TLS_AES_128_CCM_SHA256")
            .put(TlsAes128GcmSha256.VALUE,                      "TLS_AES_128_GCM_SHA256")
            .put(TlsAes256GcmSha384.VALUE,                      "TLS_AES_256_GCM_SHA384")
            .put(TlsChacha20Poly1305Sha256.VALUE,               "TLS_CHACHA20_POLY1305_SHA256")
            .put(TlsDhePskWithAes128Ccm.VALUE,                  "TLS_DHE_PSK_WITH_AES_128_CCM")
            .put(TlsDhePskWithAes128GcmSha256.VALUE,            "TLS_DHE_PSK_WITH_AES_128_GCM_SHA256")
            .put(TlsDhePskWithAes256Ccm.VALUE,                  "TLS_DHE_PSK_WITH_AES_256_CCM")
            .put(TlsDhePskWithAes256GcmSha384.VALUE,            "TLS_DHE_PSK_WITH_AES_256_GCM_SHA384")
            .put(TlsDhePskWithChacha20Poly1305Sha256.VALUE,     "TLS_DHE_PSK_WITH_CHACHA20_POLY1305_SHA256")
            .put(TlsDheRsaWithAes128Ccm.VALUE,                  "TLS_DHE_RSA_WITH_AES_128_CCM")
            .put(TlsDheRsaWithAes128GcmSha256.VALUE,            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256")
            .put(TlsDheRsaWithAes256Ccm.VALUE,                  "TLS_DHE_RSA_WITH_AES_256_CCM")
            .put(TlsDheRsaWithAes256GcmSha384.VALUE,            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384")
            .put(TlsDheRsaWithChacha20Poly1305Sha256.VALUE,     "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256")
            .put(TlsEcdheEcdsaWithAes128GcmSha256.VALUE,        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
            .put(TlsEcdheEcdsaWithAes256GcmSha384.VALUE,        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384")
            .put(TlsEcdheEcdsaWithChacha20Poly1305Sha256.VALUE, "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256")
            .put(TlsEcdhePskWithAes128CcmSha256.VALUE,          "TLS_ECDHE_PSK_WITH_AES_128_CCM_SHA256")
            .put(TlsEcdhePskWithAes128GcmSha256.VALUE,          "TLS_ECDHE_PSK_WITH_AES_128_GCM_SHA256")
            .put(TlsEcdhePskWithAes256GcmSha384.VALUE,          "TLS_ECDHE_PSK_WITH_AES_256_GCM_SHA384")
            .put(TlsEcdhePskWithChacha20Poly1305Sha256.VALUE,   "TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256")
            .put(TlsEcdheRsaWithAes128GcmSha256.VALUE,          "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
            .put(TlsEcdheRsaWithAes256GcmSha384.VALUE,          "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")
            .put(TlsEcdheRsaWithChacha20Poly1305Sha256.VALUE,   "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256")
            .build();

    private volatile @NonNull SslContext sslContext;

    TLSTransportStack(final TransportChannelListener listener, final SslContext sslContext) {
        super(listener);
        this.sslContext = requireNonNull(sslContext);
    }

    @Override
    protected final void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        final var channel = underlayChannel.channel();
        final var sslHandler = sslContext.newHandler(channel.alloc());

        channel.pipeline().addLast(sslHandler);
        sslHandler.handshakeFuture().addListener(future -> {
            final var cause = future.cause();
            if (cause != null) {
                notifyTransportChannelFailed(cause);
                channel.close();
            } else {
                addTransportChannel(new TLSTransportChannel(underlayChannel));
            }
        });
    }

    final void setSslContext(final SslContext sslContext) {
        this.sslContext = requireNonNull(sslContext);
    }

    static KeyStore loadKeyStore(final KeyStore existing, final String aliasPrefix,
            final LocalOrTruststoreCertsGrouping certs) throws UnsupportedConfigurationException {
        final var keyStore = existing != null ? existing : newKeyStore();

        final var localOrTruststore = certs.getLocalOrTruststore();
        if (!(localOrTruststore
            instanceof org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.local.or
                       .truststore.certs.grouping.local.or.truststore.Local local)) {
            throw new UnsupportedConfigurationException("Unsupported certificate " + localOrTruststore);
        }
        final var localDef = local.getLocalDefinition();
        if (localDef == null) {
            throw new UnsupportedConfigurationException("Missing local definition in " + local);
        }
        for (var cert : localDef.nonnullCertificate().values()) {
            try {
                keyStore.setCertificateEntry(aliasPrefix + cert.requireName(), loadCertificate(cert.requireCertData()));
            } catch (KeyStoreException e) {
                throw new UnsupportedConfigurationException("Failed to load " + cert, e);
            }
        }

        return keyStore;
    }

    private static Certificate loadCertificate(final TrustAnchorCertCms certCms)
            throws UnsupportedConfigurationException {
        // FIXME: implement this
        throw new UnsupportedConfigurationException("Unsupported certificate " + certCms);
    }

    private static KeyStore newKeyStore() throws UnsupportedConfigurationException {
        final KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
        } catch (NoSuchAlgorithmException | CertificateException | IOException | KeyStoreException e) {
            throw new UnsupportedConfigurationException("Cannot instantiate key store", e);
        }
        return keyStore;
    }

    static final KeyManager newKeyManager(final @NonNull LocalOrKeystoreEndEntityCertWithKeyGrouping certificate)
            throws UnsupportedConfigurationException {
        final var localOrKeystore = certificate.getLocalOrKeystore();
        if (!(localOrKeystore instanceof Local local)) {
            throw new UnsupportedConfigurationException("Unsupported certificate " + localOrKeystore);
        }
        final var localDef = local.getLocalDefinition();
        if (localDef == null) {
            throw new UnsupportedConfigurationException("Missing local definition in " + local);
        }
        final var keyStore = loadKeyPair(localDef);

        final var certData = localDef.getCertData();
        if (certData == null) {
            throw new UnsupportedConfigurationException("Missing certificate definition in " + localDef);
        }

        // FIXME: implement this

        return newKeyManager(keyStore);
    }

    static final KeyManager newKeyManager(final @NonNull LocalOrKeystoreAsymmetricKeyGrouping rawPrivateKey)
            throws UnsupportedConfigurationException {
        final var localOrKeystore = rawPrivateKey.getLocalOrKeystore();
        if (!(localOrKeystore
            instanceof org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.local.or
                       .keystore.asymmetric.key.grouping.local.or.keystore.Local local)) {
            throw new UnsupportedConfigurationException("Unsupported key " + localOrKeystore);
        }
        final var localDef = local.getLocalDefinition();
        if (localDef == null) {
            throw new UnsupportedConfigurationException("Missing local definition in " + local);
        }
        return newKeyManager(loadKeyPair(localDef));
    }

    static final KeyManager newKeyManager(final KeyStore keyStore) throws UnsupportedConfigurationException {
        final KeyManagerFactory kmf;
        try {
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedConfigurationException("Cannot instantiate key manager", e);
        }
        try {
            kmf.init(keyStore, EMPTY_CHARS);
        } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new UnsupportedConfigurationException("Cannot initialize key manager", e);
        }

        final var managers = kmf.getKeyManagers();

        throw new UnsupportedConfigurationException("FIXME: implement this");
    }

    private static KeyStore loadKeyPair(final AsymmetricKeyPairGrouping keyPair)
            throws UnsupportedConfigurationException {
        final byte[] privateBytes;
        final var privateType = keyPair.getPrivateKeyType();
        if (privateType instanceof CleartextPrivateKey clearText) {
            privateBytes = clearText.requireCleartextPrivateKey();
        } else {
            throw new UnsupportedConfigurationException("Unsupported private key " + privateType);
        }

        final String privateAlgorithm;
        final KeySpec privateSpec;
        final var privateFormat = keyPair.getPrivateKeyFormat();
        if (EcPrivateKeyFormat.VALUE.equals(privateFormat)) {
            privateAlgorithm = "EC";
            // FIXME: load from bytes ECPrivateKey (from RFC5915)
            privateSpec = new ECPrivateKeySpec(null, null);
        } else if (RsaPrivateKeyFormat.VALUE.equals(privateFormat)) {
            privateAlgorithm = "RSA";
            // FIXME: load from bytes RSAPrivateKey (from RFC3447)
            privateSpec = new RSAPrivateKeySpec(null, null);
        } else {
            throw new UnsupportedConfigurationException("Unsupported private key format " + privateFormat);
        }

        final KeyFactory privateFactory;
        try {
            privateFactory = KeyFactory.getInstance(privateAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedConfigurationException("Cannot handle private key format " + privateFormat, e);
        }

        final KeyStore keyStore = newKeyStore();
        try {
            keyStore.setKeyEntry("private", privateFactory.generatePrivate(privateSpec), EMPTY_CHARS, null);
        } catch (KeyStoreException | InvalidKeySpecException e) {
            throw new UnsupportedConfigurationException("Failed to load private key", e);
        }

        final var publicFormat = keyPair.getPublicKeyFormat();
        if (SubjectPublicKeyInfoFormat.VALUE.equals(publicFormat)) {
            // FIXME: load from bytes: SubjectPublicKeyInfo (from RFC5280) DER-encoded
        } else {
            throw new UnsupportedConfigurationException("Unsupported private key format " + publicFormat);
        }


        keyPair.getPublicKey();

        // FIXME: implement this
        throw new UnsupportedConfigurationException("Unsupported key " + keyPair);
    }

    // FIXME: should be TrustManagerBuilder
    protected static final @Nullable X509TrustManager newTrustManager(
            final @Nullable LocalOrTruststoreCertsGrouping caCerts,
            final @Nullable LocalOrTruststoreCertsGrouping eeCerts,
            final @Nullable LocalOrTruststorePublicKeysGrouping publicKeys) throws UnsupportedConfigurationException {
        KeyStore keyStore = null;
        if (caCerts != null) {
            keyStore = loadKeyStore(null, "ca-", caCerts);
        }
        if (eeCerts != null) {
            keyStore = loadKeyStore(keyStore, "ee-", eeCerts);
        }
        if (publicKeys != null) {
            // FIXME: implement this and advertize server-auth-raw-public-key from IetfTlsClientFeatureProvider
            throw new UnsupportedConfigurationException("Public key authentication not implemented");
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

        final var managers = builder.build();
        return switch (managers.size()) {
            case 0 -> null;
            case 1 -> managers.get(0);
            default -> new CompositeX509TrustManager(managers);
        };
    }

    static final SslContext buildSslContext(final SslContextBuilder builder, final HelloParamsGrouping helloParams)
            throws UnsupportedConfigurationException {
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

    private static String[] createTlsStrings(final Set<TlsVersionBase> versions)
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
