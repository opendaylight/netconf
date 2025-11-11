/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.crypto;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.ThreadLocalRandom;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.AsymmetricKeyPairGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.AsymmetricKeyPairWithCertGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010._private.key.grouping._private.key.type.CleartextPrivateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support for parsing public/private key pairs encoded as {@link AsymmetricKeyPairGrouping}.
 *
 * <p>This class is considered a Beta-level API for internal use. It can change incompatibly between minor releases.
 *
 * @since 10.0.1
 */
// FIXME: NETCONF-1536: this should be a component with pluggable support for various formats
@Beta
@NonNullByDefault
public final class KeyPairParser {
    /**
     * A {@link KeyPair} and a {@link Certificate}, i.e. the equivalent of {@link AsymmetricKeyPairWithCertGrouping}.
     */
    // Note: not a record to prevent outside construction
    public static final class KeyPairWithCertificate {
        private final KeyPair keyPair;
        private final Certificate certificate;

        KeyPairWithCertificate(final KeyPair keyPair, final Certificate certificate) {
            this.keyPair = requireNonNull(keyPair);
            this.certificate = requireNonNull(certificate);
        }

        /**
         * {@return the {@link KeyPair}}
         */
        public KeyPair keyPair() {
            return keyPair;
        }

        /**
         * {@return the {@link Certificate}}
         */
        public Certificate certificate() {
            return certificate;
        }

        @Override
        public String toString() {
            return "CertifiedKeyPair [keyPair=" + keyPair + ", certificate=" + certificate + "]";
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(KeyPairParser.class);

    private KeyPairParser() {
        // Hidden on purpose
    }

    /**
     * Parse an {@link AsymmetricKeyPairGrouping} into a {@link KeyPair}.
     *
     * @param config the {@link AsymmetricKeyPairGrouping}
     * @return a {@link KeyPair}
     * @throws UnsupportedConfigurationException if the key pair cannot be parsed
     */
    public static KeyPair parseKeyPair(final AsymmetricKeyPairGrouping config)
            throws UnsupportedConfigurationException {
        // Private key is mandatory, interpret it first, which gives us the algorithm we ware supporting
        final var privFormat = config.getPrivateKeyFormat();
        if (privFormat == null) {
            throw new UnsupportedConfigurationException("Missing private key format");
        }
        final var keyFactory = getKeyFactory(privFormat);

        final var privType = config.getPrivateKeyType();
        final var privBody = switch (privType) {
            case CleartextPrivateKey clearText -> {
                final var key = clearText.getCleartextPrivateKey();
                if (key == null) {
                    throw new UnsupportedConfigurationException("Missing private key");
                }
                yield key;
            }
            // FIXME: NETCONF-1536: EncryptedPrivateKey, HiddenPrivateKey and others?
            default -> throw new UnsupportedConfigurationException("Unsupported private key type " + privType);
        };
        final PrivateKey privKey;
        try {
            privKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privBody));
        } catch (InvalidKeySpecException e) {
            throw new UnsupportedConfigurationException("Invalid private key for " + keyFactory.getAlgorithm(), e);
        }

        // Next up: public key is optional and composed of two parts. If any of them are missing derive the public key
        //          from the private key.
        final var pubBody = config.getPublicKey();
        if (pubBody == null) {
            LOG.debug("Missing public key, deriving it from the private key");
            return privateOnlyKeyPair(privFormat, keyFactory, privKey);
        }
        final var pubFormat = config.getPublicKeyFormat();
        if (pubFormat == null) {
            LOG.warn("Missing public key format, deriving public key from the private key");
            return privateOnlyKeyPair(privFormat, keyFactory, privKey);
        }
        final var pubKey = PublicKeyParser.parsePublicKey(pubFormat, pubBody);

        // Validate we have a matching pair
        // FIXME: NETCONF-1536: there just has to be a better of doing this
        final var signAlgorithm = switch (privKey) {
            case ECPrivateKey ec -> "SHA256withECDSA";
            case RSAPrivateKey rsa -> "SHA256withRSA";
            default -> throw new UnsupportedConfigurationException("Unsupported key type " + privKey);
        };

        try {
            // test data
            final byte[] original = new byte[1024];
            ThreadLocalRandom.current().nextBytes(original);
            // sign using the private key
            final Signature signature = Signature.getInstance(signAlgorithm);
            signature.initSign(privKey);
            signature.update(original);
            final byte[] signed = signature.sign();
            // verify using the public key
            signature.initVerify(pubKey);
            signature.update(original);
            if (!signature.verify(signed)) {
                throw new UnsupportedConfigurationException("Private key mismatches Public key");
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new UnsupportedConfigurationException("Key pair validation failed", e);
        }

        return new KeyPair(pubKey, privKey);
    }

    /**
     * Parse an {@link AsymmetricKeyPairWithCertGrouping} into a {@link KeyPairWithCertificate}.
     *
     * @param config the {@link AsymmetricKeyPairWithCertGrouping}
     * @return a {@link KeyPairWithCertificate}
     * @throws UnsupportedConfigurationException if the key pair or certificate cannot be parsed or if the certificate's
     *         public key does not match the kair pair
     */
    public static KeyPairWithCertificate parseKeyPairWithCertificate(final AsymmetricKeyPairWithCertGrouping config)
            throws UnsupportedConfigurationException {
        final var certData = config.getCertData();
        if (certData == null) {
            throw new UnsupportedConfigurationException("Missing certificate");
        }
        final var cert = CMSCertificateParser.parseCertificate(certData);
        final var keyPair = parseKeyPair(config);
        // FIXME: NETCONF-1536: better key comparison
        if (!keyPair.getPublic().equals(cert.getPublicKey())) {
            throw new UnsupportedConfigurationException("Certificate does not match the public key");
        }
        return new KeyPairWithCertificate(keyPair, cert);
    }

    // get the KeyFactory corresponding to a PrivateKeyFormat
    private static KeyFactory getKeyFactory(final PrivateKeyFormat format) throws UnsupportedConfigurationException {
        try {
            if (EcPrivateKeyFormat.VALUE.equals(format)) {
                return KeyFactory.getInstance("EC", BCHolder.PROV);
            }
            if (RsaPrivateKeyFormat.VALUE.equals(format)) {
                return KeyFactory.getInstance("RSA", BCHolder.PROV);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedConfigurationException("Unsupported private key format " + format, e);
        }
        throw new UnsupportedConfigurationException("Unsupported private key format " + format);
    }

    // return a KeyPair with public key derived from the private key
    private static KeyPair privateOnlyKeyPair(final PrivateKeyFormat format, final KeyFactory factory, PrivateKey key)
            throws UnsupportedOperationException {
        // FIXME: NETCONF-1536: implement this
        throw new UnsupportedOperationException("Cannot derive public keys for " + format);
    }
}
