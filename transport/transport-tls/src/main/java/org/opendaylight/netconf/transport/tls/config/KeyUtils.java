/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls.config;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

class KeyUtils {

    static PrivateKey buildPrivateKey(final String keyAlgorithm, final byte[] bytes)
            throws UnsupportedConfigurationException {
        try {
            return KeyFactory.getInstance(keyAlgorithm).generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new UnsupportedConfigurationException("Cannot build private key for " + keyAlgorithm, e);
        }
    }

    static PublicKey buildX509PublicKey(final String keyAlgorithm, final byte[] bytes)
            throws UnsupportedConfigurationException {
        try {
            return KeyFactory.getInstance(keyAlgorithm).generatePublic(new X509EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new UnsupportedConfigurationException("Cannot build private key for " + keyAlgorithm, e);
        }
    }

    static PublicKey buildPublicKeyFromSshEncoding(final byte[] bytes)
            throws UnsupportedConfigurationException {

        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String type = readString(in);
            if (type.equals("ssh-rsa")) {
                final BigInteger exponent = readBigInteger(in);
                final BigInteger modulus = readBigInteger(in);
                final RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                return KeyFactory.getInstance("RSA").generatePublic(spec);

            } else if (type.equals("ssh-dss")) {
                final BigInteger pValue = readBigInteger(in);
                final BigInteger qValue = readBigInteger(in);
                final BigInteger gValue = readBigInteger(in);
                final BigInteger yValue = readBigInteger(in);
                final DSAPublicKeySpec spec = new DSAPublicKeySpec(yValue, pValue, qValue, gValue);
                return KeyFactory.getInstance("DSA").generatePublic(spec);

            } else if (type.startsWith("ecdsa-sha2-") &&
                    (type.endsWith("nistp256") || type.endsWith("nistp384") || type.endsWith("nistp521"))) {
                final String identifier = readString(in);
                final BigInteger q = readBigInteger(in);
                final String algName = identifier.replace("nist", "sec") + "r1";
                final ECPoint ecPoint = buildECPoint(q, algName);
                final ECParameterSpec ecParameterSpec = buildECParameterSpec(algName);
                final ECPublicKeySpec spec = new ECPublicKeySpec(ecPoint, ecParameterSpec);
                return KeyFactory.getInstance("EC").generatePublic(spec);

            } else {
                throw new IllegalArgumentException("unknown type " + type);
            }
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new UnsupportedConfigurationException("Cannot build public key from ssh bytes", e);
        }
    }

    private static String readString(final DataInputStream in) throws IOException {
        final int len = in.readInt();
        return new String(in.readNBytes(len));
    }

    private static BigInteger readBigInteger(final DataInputStream in) throws IOException {
        final int len = in.readInt();
        return new BigInteger(in.readNBytes(len));
    }

    private static ECPoint buildECPoint(final BigInteger qValue, final String algName) {
        final ECNamedCurveParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec(algName);
        final org.bouncycastle.math.ec.ECPoint point = ecSpec.getCurve().decodePoint(qValue.toByteArray());
        return new ECPoint(point.getAffineXCoord().toBigInteger(), point.getAffineYCoord().toBigInteger());
    }

    private static ECParameterSpec buildECParameterSpec(String algName) {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(algName));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (InvalidParameterSpecException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unable to get parameter spec for algorithm " + algName, e);
        }
    }

    static void validateKeyPair(final PublicKey publicKey, final PrivateKey privateKey)
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
