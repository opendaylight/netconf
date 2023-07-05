/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.AsymmetricKeyPairGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.SshPublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.asymmetric.key.pair.grouping._private.key.type.CleartextPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417.InlineOrKeystoreAsymmetricKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417.InlineOrKeystoreEndEntityCertWithKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417.InlineOrTruststoreCertsGrouping;

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
            final @Nullable InlineOrTruststoreCertsGrouping caCerts,
            final @Nullable InlineOrTruststoreCertsGrouping eeCerts) throws UnsupportedConfigurationException {
        var certMap = ImmutableMap.<String, Certificate>builder()
                .putAll(extractCertificates(caCerts, "ca-"))
                .putAll(extractCertificates(eeCerts, "ee-"))
                .build();
        for (var entry : certMap.entrySet()) {
            try {
                keyStore.setCertificateEntry(entry.getKey(), entry.getValue());
            } catch (KeyStoreException e) {
                throw new UnsupportedConfigurationException("Failed to load certificate", e);
            }
        }
    }

    private static Map<String, Certificate> extractCertificates(
            @Nullable final InlineOrTruststoreCertsGrouping certs,
            @NonNull final String aliasPrefix) throws UnsupportedConfigurationException {
        if (certs == null) {
            return Map.of();
        }
        final var inline = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore
                        .rev230417.inline.or.truststore.certs.grouping.inline.or.truststore.Inline.class,
                certs.getInlineOrTruststore());
        final var inlineDef = inline.getInlineDefinition();
        if (inlineDef == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inline);
        }
        final var mapBuilder = ImmutableMap.<String, Certificate>builder();
        for (var cert : inlineDef.nonnullCertificate().values()) {
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
            final @NonNull InlineOrKeystoreAsymmetricKeyGrouping input)
            throws UnsupportedConfigurationException {

        final var inline = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417
                        .inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.Inline.class,
                input.getInlineOrKeystore());
        final var inlineDef = inline.getInlineDefinition();
        if (inlineDef == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inline);
        }
        final var keyPair = extractKeyPair(inlineDef);
        // ietf-crypto-types:grouping asymmetric-key-pair-grouping
        // "A private key and its associated public key.  Implementations
        // SHOULD ensure that the two keys are a matching pair."
        validateKeyPair(keyPair.getPublic(), keyPair.getPrivate());
        try {
            // FIXME: the below line throws an exception bc keyStore does not support private key without certificate
            //        chain (belongs to implementation of raw public key feature support)
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
            final @NonNull InlineOrKeystoreEndEntityCertWithKeyGrouping input)
                throws UnsupportedConfigurationException {
        final var inline = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417
                        .inline.or.keystore.end.entity.cert.with.key.grouping.inline.or.keystore.Inline.class,
                input.getInlineOrKeystore());
        final var inlineDef = inline.getInlineDefinition();
        if (inlineDef == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inline);
        }
        final var keyPair = extractKeyPair(inlineDef);
        final Certificate certificate;
        try {
            certificate = buildX509Certificate(inlineDef.requireCertData().getValue());
        } catch (IOException | CertificateException e) {
            throw new UnsupportedConfigurationException("Failed to load certificate" + inlineDef, e);
        }
        // ietf-crypto-types:asymmetric-key-pair-with-cert-grouping
        // "A private/public key pair and an associated certificate.
        // Implementations SHOULD assert that certificates contain the matching public key."
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
