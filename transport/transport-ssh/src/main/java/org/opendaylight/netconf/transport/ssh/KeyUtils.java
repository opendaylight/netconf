/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.AsymmetricKeyPairGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SshPublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010._private.key.grouping._private.key.type.CleartextPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.InlineOrKeystore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.Inline;

final class KeyUtils {
    private static final String RSA_ALGORITHM = "RSA";
    private static final String EC_ALGORITHM = "EC";
    private static final String BC = BouncyCastleProvider.PROVIDER_NAME;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private KeyUtils() {
        // utility class
    }

    static X509Certificate buildX509Certificate(final byte[] bytes) throws UnsupportedConfigurationException {
        try (var in = new ByteArrayInputStream(bytes)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509", BC).generateCertificate(in);
        } catch (IOException | CertificateException | NoSuchProviderException e) {
            throw new UnsupportedConfigurationException("Cannot read certificate", e);
        }
    }

    static PrivateKey buildPrivateKey(final String keyAlgorithm, final byte[] bytes)
            throws UnsupportedConfigurationException {
        try {
            return KeyFactory.getInstance(keyAlgorithm, BC).generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException e) {
            throw new UnsupportedConfigurationException("Cannot build private key for " + keyAlgorithm, e);
        }
    }

    static PublicKey buildX509PublicKey(final String keyAlgorithm, final byte[] bytes)
            throws UnsupportedConfigurationException {
        try {
            return KeyFactory.getInstance(keyAlgorithm, BC).generatePublic(new X509EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException e) {
            throw new UnsupportedConfigurationException("Cannot build public key for " + keyAlgorithm, e);
        }
    }

    static PublicKey buildPublicKeyFromSshEncoding(final byte[] bytes) throws UnsupportedConfigurationException {
        try {
            var parsed = OpenSSHPublicKeyUtil.parsePublicKey(bytes);
            var converted = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(parsed).getEncoded();
            if (parsed instanceof RSAKeyParameters rsaParams && !rsaParams.isPrivate()) {
                return buildX509PublicKey(RSA_ALGORITHM, converted);
            }
            if (parsed instanceof ECPublicKeyParameters ecParams && !ecParams.isPrivate()) {
                return buildX509PublicKey(EC_ALGORITHM, converted);
            }
            throw new UnsupportedConfigurationException("Invalid OpenSSH public key; "
                    + "Expected RSA or EC public key; Current:" + parsed);
        } catch (IOException e) {
            throw new UnsupportedConfigurationException("Cannot parse OpenSSH public key", e);
        }
    }

    static KeyPair extractKeyPair(final InlineOrKeystore input) throws UnsupportedConfigurationException {
        final var inline = ConfigUtils.ofType(Inline.class, input);
        final var inlineDef = inline.getInlineDefinition();
        if (inlineDef == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inline);
        }
        return extractKeyPair(inlineDef);
    }

    static KeyPair extractKeyPair(final AsymmetricKeyPairGrouping input) throws UnsupportedConfigurationException {
        final var keyFormat = input.getPrivateKeyFormat();
        final String privateKeyAlgorithm;
        if (EcPrivateKeyFormat.VALUE.equals(keyFormat)) {
            privateKeyAlgorithm = EC_ALGORITHM;
        } else if (RsaPrivateKeyFormat.VALUE.equals(keyFormat)) {
            privateKeyAlgorithm = RSA_ALGORITHM;
        } else {
            throw new UnsupportedConfigurationException("Unsupported private key format " + keyFormat);
        }
        final byte[] privateKeyBytes;
        if (!(input.getPrivateKeyType() instanceof CleartextPrivateKey clearText)) {
            throw new UnsupportedConfigurationException("Unsupported private key type " + input.getPrivateKeyType());
        }
        privateKeyBytes = clearText.requireCleartextPrivateKey();

        final var publicKeyFormat = input.getPublicKeyFormat();
        final var publicKeyBytes = input.getPublicKey();
        final boolean isSshPublicKey;
        if (SubjectPublicKeyInfoFormat.VALUE.equals(publicKeyFormat)) {
            isSshPublicKey = false;
        } else if (SshPublicKeyFormat.VALUE.equals(publicKeyFormat)) {
            isSshPublicKey = true;
        } else {
            throw new UnsupportedConfigurationException("Unsupported public key format " + publicKeyFormat);
        }

        final var privateKey = KeyUtils.buildPrivateKey(privateKeyAlgorithm, privateKeyBytes);
        final var publicKey = isSshPublicKey ? KeyUtils.buildPublicKeyFromSshEncoding(publicKeyBytes)
                : KeyUtils.buildX509PublicKey(privateKeyAlgorithm, publicKeyBytes);
        /*
            ietf-crypto-types:grouping asymmetric-key-pair-grouping
            "A private key and its associated public key.  Implementations
            SHOULD ensure that the two keys are a matching pair."
         */
        validateKeyPair(publicKey, privateKey);
        return new KeyPair(publicKey, privateKey);
    }

    private static void validateKeyPair(final PublicKey publicKey, final PrivateKey privateKey)
            throws UnsupportedConfigurationException {
        final String signAlgorithm;
        if (privateKey instanceof RSAPrivateKey) {
            signAlgorithm = "SHA256withRSA";
        } else if (privateKey instanceof ECPrivateKey) {
            signAlgorithm = "SHA256withECDSA";
        } else {
            throw new UnsupportedConfigurationException("Unsupported key type " + privateKey);
        }
        try {
            // test data
            final byte[] original = new byte[1024];
            ThreadLocalRandom.current().nextBytes(original);
            // sign using the private key
            final Signature signature = Signature.getInstance(signAlgorithm);
            signature.initSign(privateKey);
            signature.update(original);
            final byte[] signed = signature.sign();
            // verify using the public key
            signature.initVerify(publicKey);
            signature.update(original);
            if (!signature.verify(signed)) {
                throw new UnsupportedConfigurationException("Private key mismatches Public key");
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new UnsupportedConfigurationException("Key pair validation failed", e);
        }
    }

    static void validatePublicKey(final PublicKey publicKey, final Certificate certificate)
            throws UnsupportedConfigurationException {
        if (!Objects.equals(publicKey, certificate.getPublicKey())) {
            throw new UnsupportedConfigurationException("Certificate mismatches Public key");
        }
    }
}
