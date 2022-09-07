/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls.config;

import static java.util.Optional.ofNullable;
import static org.opendaylight.netconf.transport.tls.config.KeyStoreUtils.buildX509Certificate;
import static org.opendaylight.netconf.transport.tls.config.KeyUtils.buildPrivateKey;
import static org.opendaylight.netconf.transport.tls.config.KeyUtils.buildPublicKeyFromSshEncoding;
import static org.opendaylight.netconf.transport.tls.config.KeyUtils.buildX509PublicKey;
import static org.opendaylight.netconf.transport.tls.config.KeyUtils.validateKeyPair;
import static org.opendaylight.netconf.transport.tls.config.KeyUtils.validatePublicKey;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.AsymmetricKeyPairGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.PrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.PublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.SshPublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.asymmetric.key.pair.grouping.PrivateKeyType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.asymmetric.key.pair.grouping._private.key.type.CleartextPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.LocalOrKeystoreAsymmetricKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.LocalOrKeystoreEndEntityCertWithKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.LocalOrTruststoreCertsGrouping;

public final class ConfigUtils {

    static final char[] EMPTY_SECRET = new char[0];
    static final String DEFAULT_PRIVATE_KEY_ALIAS = "private";
    static final String DEFAULT_CERTIFICATE_ALIAS = "certificate";

    private ConfigUtils() {
        // utility class
    }

    /**
     * Builds X.509 certificates based on configuration data provided then sets them to given key store.
     *
     * @param keyStore key store
     * @param caCerts CA certificates configuration
     * @param eeCerts EE certificates configuration
     * @throws UnsupportedConfigurationException if error occurs
     */
    public static void setX509Certificates(final @NonNull KeyStore keyStore,
            final @Nullable LocalOrTruststoreCertsGrouping caCerts,
            final @Nullable LocalOrTruststoreCertsGrouping eeCerts) throws UnsupportedConfigurationException {
        var certInfos = ImmutableList.<X509CertificateInfo>builder()
                .addAll(collectCertificateInfo(caCerts, "ca-"))
                .addAll(collectCertificateInfo(eeCerts, "ee-")).build();
        setX509Certificates(keyStore, certInfos);
    }

    private static void setX509Certificates(@NonNull final KeyStore keyStore,
            @NonNull final List<X509CertificateInfo> certInfos) throws UnsupportedConfigurationException {
        for (var certInfo : certInfos) {
            try {
                keyStore.setCertificateEntry(certInfo.name(), buildX509Certificate(certInfo.bytes()));
            } catch (CertificateException | KeyStoreException | IOException e) {
                throw new UnsupportedConfigurationException("Failed to load certificate" + certInfo, e);
            }
        }
    }

    private static List<X509CertificateInfo> collectCertificateInfo(
            @Nullable final LocalOrTruststoreCertsGrouping certs,
            @NonNull final String aliasPrefix) throws UnsupportedConfigurationException {
        if (certs == null) {
            return List.of();
        }
        final var local = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore
                        .rev221212.local.or.truststore.certs.grouping.local.or.truststore.Local.class,
                certs.getLocalOrTruststore());
        final var localDef = ofNullable(local.getLocalDefinition())
                .orElseThrow(() -> new UnsupportedConfigurationException("Missing local definition in " + local));
        final var listBuilder = ImmutableList.<X509CertificateInfo>builder();
        for (var cert : localDef.nonnullCertificate().values()) {
            final var certAlias = aliasPrefix + cert.requireName();
            final var certBytes = cert.requireCertData().getValue();
            listBuilder.add(new X509CertificateInfo(certAlias, certBytes));
        }
        return listBuilder.build();
    }



    /**
     * Builds asymmetric key pair from configuration data provided, validates it then puts into given key store.
     *
     * @param keyStore keystore
     * @param input configuration
     * @throws UnsupportedConfigurationException if key pair is not set to key store
     */
    public static void setAsymmetricKey(final @NonNull KeyStore keyStore,
            final @NonNull LocalOrKeystoreAsymmetricKeyGrouping input)
            throws UnsupportedConfigurationException {
        /*
            ietf-crypto-types:grouping asymmetric-key-pair-grouping
            "A private key and its associated public key.  Implementations
            SHOULD ensure that the two keys are a matching pair."
         */
        final var local = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
                        .local.or.keystore.asymmetric.key.grouping.local.or.keystore.Local.class,
                input.getLocalOrKeystore());
        final var localDef = ofNullable(local.getLocalDefinition())
                .orElseThrow(() -> new UnsupportedConfigurationException("Missing local definition in " + local));
        setPrivateKeyFromKeyPair(keyStore, collectKeyPairInfo(localDef));
    }

    /**
     * Builds asymmetric key pair and associated certificate from configuration data provided, validates
     * then puts into given key store.
     *
     * @param keyStore key store
     * @param input configuration
     * @throws UnsupportedConfigurationException if key pair and certificate are not set to key store
     */
    public static void setEndEntityCertificateWithKey(final @NonNull KeyStore keyStore,
            final @NonNull LocalOrKeystoreEndEntityCertWithKeyGrouping input) throws UnsupportedConfigurationException {
        /*
          ietf-crypto-types:asymmetric-key-pair-with-cert-grouping
          "A private/public key pair and an associated certificate.
          Implementations SHOULD assert that certificates contain the matching public key.";
         */
        final var local = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
                        .local.or.keystore.end.entity.cert.with.key.grouping.local.or.keystore.Local.class,
                input.getLocalOrKeystore());
        final var localDef = ofNullable(local.getLocalDefinition())
                .orElseThrow(() -> new UnsupportedConfigurationException("Missing local definition in " + local));

        final var keyPairInfo = collectKeyPairInfo(localDef);
        final var privateKey = buildPrivateKey(keyPairInfo.privateKeyFormat().name(), keyPairInfo.privateKeyBytes());
        final var publicKey = KeyPairInfo.PublicKeyFormat.SSH.equals(keyPairInfo.publicKeyFormat())
                ? buildPublicKeyFromSshEncoding(keyPairInfo.publicKeyBytes())
                : buildX509PublicKey(keyPairInfo.privateKeyFormat().name(), keyPairInfo.publicKeyBytes());
        final Certificate certificate;
        try {
            certificate = buildX509Certificate(localDef.requireCertData().getValue());
        } catch (IOException | CertificateException e) {
            throw new UnsupportedConfigurationException("Failed to load certificate" + localDef, e);
        }
        validateKeyPair(publicKey, privateKey);
        validatePublicKey(publicKey, certificate);
        try {
            keyStore.setCertificateEntry(DEFAULT_CERTIFICATE_ALIAS, certificate);
            keyStore.setKeyEntry(DEFAULT_PRIVATE_KEY_ALIAS, privateKey, EMPTY_SECRET, new Certificate[]{certificate});
        } catch (KeyStoreException e) {
            throw new UnsupportedConfigurationException("Failed to load certificate and/or private key", e);
        }
    }

    private static void setPrivateKeyFromKeyPair(@NonNull final KeyStore keyStore, final KeyPairInfo keyPairInfo)
            throws UnsupportedConfigurationException {
        final var privateKey = buildPrivateKey(keyPairInfo.privateKeyFormat().name(), keyPairInfo.privateKeyBytes());
        final var publicKey = KeyPairInfo.PublicKeyFormat.SSH.equals(keyPairInfo.publicKeyFormat())
                ? buildPublicKeyFromSshEncoding(keyPairInfo.publicKeyBytes())
                : buildX509PublicKey(keyPairInfo.privateKeyFormat().name(), keyPairInfo.publicKeyBytes());
        validateKeyPair(publicKey, privateKey);
        try {
            // FIXME
            // below line throws an exception bc keyStore does not support private key without certificate chain
            // (belongs to implementation of raw public key feature support)
            keyStore.setKeyEntry(DEFAULT_PRIVATE_KEY_ALIAS, privateKey, EMPTY_SECRET, null);
        } catch (KeyStoreException e) {
            throw new UnsupportedConfigurationException("Failed to load private key", e);
        }
    }

    private static KeyPairInfo collectKeyPairInfo(final AsymmetricKeyPairGrouping keyPair)
            throws UnsupportedConfigurationException {
        final var privateKeyFormat = getPrivateKeyFormat(keyPair.getPrivateKeyFormat());
        final byte[] privateKeyBytes = getPrivateKeyBytes(keyPair.getPrivateKeyType());
        final var publicKeyType = getPublicKeyType(keyPair.getPublicKeyFormat());
        return new KeyPairInfo(privateKeyFormat, privateKeyBytes, publicKeyType, keyPair.getPublicKey());
    }

    private static KeyPairInfo.PrivateKeyFormat getPrivateKeyFormat(final @Nullable PrivateKeyFormat format)
            throws UnsupportedConfigurationException {
        if (EcPrivateKeyFormat.VALUE.equals(format)) {
            return KeyPairInfo.PrivateKeyFormat.EC;
        } else if (RsaPrivateKeyFormat.VALUE.equals(format)) {
            return KeyPairInfo.PrivateKeyFormat.RSA;
        } else {
            throw new UnsupportedConfigurationException("Unsupported private key format " + format);
        }
    }

    private static byte[] getPrivateKeyBytes(final @Nullable PrivateKeyType type)
            throws UnsupportedConfigurationException {
        if (type instanceof CleartextPrivateKey clearText) {
            return clearText.requireCleartextPrivateKey();
        } else {
            throw new UnsupportedConfigurationException("Unsupported private key type " + type);
        }
    }

    private static KeyPairInfo.PublicKeyFormat getPublicKeyType(final @Nullable PublicKeyFormat format)
            throws UnsupportedConfigurationException {
        if (SubjectPublicKeyInfoFormat.VALUE.equals(format)) {
            return KeyPairInfo.PublicKeyFormat.SUBJECT_INFO;
        } else if (SshPublicKeyFormat.VALUE.equals(format)) {
            return KeyPairInfo.PublicKeyFormat.SSH;
        } else {
            throw new UnsupportedConfigurationException("Unsupported public key format " + format);
        }
    }

    private static <T> T ofType(final Class<T> expectedType, final Object obj)
            throws UnsupportedConfigurationException {
        if (!expectedType.isInstance(obj)) {
            throw new UnsupportedConfigurationException("Expected type: " + expectedType
                    + " actual: " + obj.getClass());
        }
        return expectedType.cast(obj);
    }
}
