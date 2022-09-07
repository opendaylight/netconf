/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static java.util.Optional.ofNullable;
import static org.opendaylight.netconf.transport.tls.KeyStoreUtils.buildX509Certificate;
import static org.opendaylight.netconf.transport.tls.KeyUtils.EC_ALGORITHM;
import static org.opendaylight.netconf.transport.tls.KeyUtils.RSA_ALGORITHM;
import static org.opendaylight.netconf.transport.tls.KeyUtils.buildPrivateKey;
import static org.opendaylight.netconf.transport.tls.KeyUtils.buildPublicKeyFromSshEncoding;
import static org.opendaylight.netconf.transport.tls.KeyUtils.buildX509PublicKey;
import static org.opendaylight.netconf.transport.tls.KeyUtils.validateKeyPair;
import static org.opendaylight.netconf.transport.tls.KeyUtils.validatePublicKey;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.AsymmetricKeyPairGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.SshPublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.asymmetric.key.pair.grouping._private.key.type.CleartextPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.LocalOrKeystoreAsymmetricKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.LocalOrKeystoreEndEntityCertWithKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.LocalOrTruststoreCertsGrouping;

final class ConfigUtils {

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
    static void setX509Certificates(final @NonNull KeyStore keyStore,
            final @Nullable LocalOrTruststoreCertsGrouping caCerts,
            final @Nullable LocalOrTruststoreCertsGrouping eeCerts) throws UnsupportedConfigurationException {
        var certMap = ImmutableMap.<String, Certificate>builder()
                .putAll(extractCertificates(caCerts, "ca-"))
                .putAll(extractCertificates(eeCerts, "ee-")).build();
        for (var entry : certMap.entrySet()) {
            try {
                keyStore.setCertificateEntry(entry.getKey(), entry.getValue());
            } catch (KeyStoreException e) {
                throw new UnsupportedConfigurationException("Failed to load certificate", e);
            }
        }
    }

    private static Map<String, Certificate> extractCertificates(
            @Nullable final LocalOrTruststoreCertsGrouping certs,
            @NonNull final String aliasPrefix) throws UnsupportedConfigurationException {
        if (certs == null) {
            return Map.of();
        }
        final var local = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore
                        .rev221212.local.or.truststore.certs.grouping.local.or.truststore.Local.class,
                certs.getLocalOrTruststore());
        final var localDef = ofNullable(local.getLocalDefinition())
                .orElseThrow(() -> new UnsupportedConfigurationException("Missing local definition in " + local));

        final var mapBuilder = ImmutableMap.<String, Certificate>builder();
        for (var cert : localDef.nonnullCertificate().values()) {
            try {
                final var alias = aliasPrefix + cert.requireName();
                mapBuilder.put(alias, buildX509Certificate(cert.requireCertData().getValue()));
            } catch (IOException | CertificateException e) {
                throw new UnsupportedConfigurationException("Failed to parse certificate " + cert, e);
            }
        }
        return mapBuilder.build();
    }

    /**
     * Builds asymmetric key pair from configuration data provided, validates it then puts into given key store.
     *
     * @param keyStore keystore
     * @param input configuration
     * @throws UnsupportedConfigurationException if key pair is not set to key store
     */
    static void setAsymmetricKey(final @NonNull KeyStore keyStore,
            final @NonNull LocalOrKeystoreAsymmetricKeyGrouping input)
            throws UnsupportedConfigurationException {

        final var local = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
                        .local.or.keystore.asymmetric.key.grouping.local.or.keystore.Local.class,
                input.getLocalOrKeystore());
        final var localDef = ofNullable(local.getLocalDefinition())
                .orElseThrow(() -> new UnsupportedConfigurationException("Missing local definition in " + local));
        final var keyPair = extractKeyPair(localDef);
        /*
            ietf-crypto-types:grouping asymmetric-key-pair-grouping
            "A private key and its associated public key.  Implementations
            SHOULD ensure that the two keys are a matching pair."
         */
        validateKeyPair(keyPair.getPublic(), keyPair.getPrivate());
        try {
            // FIXME
            // below line throws an exception bc keyStore does not support private key without certificate chain
            // (belongs to implementation of raw public key feature support)
            keyStore.setKeyEntry(DEFAULT_PRIVATE_KEY_ALIAS, keyPair.getPrivate(), EMPTY_SECRET, null);
        } catch (KeyStoreException e) {
            throw new UnsupportedConfigurationException("Failed to load private key", e);
        }
    }

    /**
     * Builds asymmetric key pair and associated certificate from configuration data provided, validates
     * then puts into given key store.
     *
     * @param keyStore key store
     * @param input configuration
     * @throws UnsupportedConfigurationException if key pair and certificate are not set to key store
     */
    static void setEndEntityCertificateWithKey(final @NonNull KeyStore keyStore,
            final @NonNull LocalOrKeystoreEndEntityCertWithKeyGrouping input) throws UnsupportedConfigurationException {
        final var local = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
                        .local.or.keystore.end.entity.cert.with.key.grouping.local.or.keystore.Local.class,
                input.getLocalOrKeystore());
        final var localDef = ofNullable(local.getLocalDefinition())
                .orElseThrow(() -> new UnsupportedConfigurationException("Missing local definition in " + local));

        final var keyPair = extractKeyPair(localDef);
        final Certificate certificate;
        try {
            certificate = buildX509Certificate(localDef.requireCertData().getValue());
        } catch (IOException | CertificateException e) {
            throw new UnsupportedConfigurationException("Failed to load certificate" + localDef, e);
        }
        /*
          ietf-crypto-types:asymmetric-key-pair-with-cert-grouping
          "A private/public key pair and an associated certificate.
          Implementations SHOULD assert that certificates contain the matching public key."
         */
        validateKeyPair(keyPair.getPublic(), keyPair.getPrivate());
        validatePublicKey(keyPair.getPublic(), certificate);
        try {
            keyStore.setCertificateEntry(DEFAULT_CERTIFICATE_ALIAS, certificate);
            keyStore.setKeyEntry(DEFAULT_PRIVATE_KEY_ALIAS, keyPair.getPrivate(),
                    EMPTY_SECRET, new Certificate[]{certificate});
        } catch (KeyStoreException e) {
            throw new UnsupportedConfigurationException("Failed to load certificate and/or private key", e);
        }
    }

    private static KeyPair extractKeyPair(final AsymmetricKeyPairGrouping input)
            throws UnsupportedConfigurationException {

        final var privateKeyFormat = input.getPrivateKeyFormat();
        final String keyAlgorithm;
        if (EcPrivateKeyFormat.VALUE.equals(privateKeyFormat)) {
            keyAlgorithm = EC_ALGORITHM;
        } else if (RsaPrivateKeyFormat.VALUE.equals(privateKeyFormat)) {
            keyAlgorithm = RSA_ALGORITHM;
        } else {
            throw new UnsupportedConfigurationException("Unsupported private key format " + privateKeyFormat);
        }
        final byte[] privateKeyBytes;
        if (input.getPrivateKeyType() instanceof CleartextPrivateKey clearText) {
            privateKeyBytes = clearText.requireCleartextPrivateKey();
        } else {
            throw new UnsupportedConfigurationException("Unsupported private key type " + input.getPrivateKeyType());
        }
        final var privateKey = buildPrivateKey(keyAlgorithm, privateKeyBytes);

        final var publicKeyFormat = input.getPublicKeyFormat();
        final boolean isSshPublicKey;
        if (SubjectPublicKeyInfoFormat.VALUE.equals(publicKeyFormat)) {
            isSshPublicKey = false;
        } else if (SshPublicKeyFormat.VALUE.equals(publicKeyFormat)) {
            isSshPublicKey = true;
        } else {
            throw new UnsupportedConfigurationException("Unsupported public key format " + publicKeyFormat);
        }
        final var publicKey = isSshPublicKey ? buildPublicKeyFromSshEncoding(input.getPublicKey())
                : buildX509PublicKey(keyAlgorithm, input.getPublicKey());
        return new KeyPair(publicKey, privateKey);
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
