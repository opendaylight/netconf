/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.ECPointUtil;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FIXME: This should be probably located at AAA library.
 */
public class AuthorizedKeysDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorizedKeysDecoder.class);

    private static final ImmutableMap<String, String> ECDSA_CURVES = ImmutableMap.<String, String>builder()
        .put("nistp256", "secp256r1")
        .put("nistp384", "secp384r1")
        .put("nistp512", "secp512r1")
        .build();

    private static final String ECDSA_SUPPORTED_CURVE_NAME = "nistp256";
    private static final String ECDSA_SUPPORTED_CURVE_NAME_SPEC = ECDSA_CURVES.get(ECDSA_SUPPORTED_CURVE_NAME);

    private static final String KEY_TYPE_RSA = "ssh-rsa";
    private static final String KEY_TYPE_DSA = "ssh-dss";
    private static final String KEY_TYPE_ECDSA = "ecdsa-sha2-" + ECDSA_SUPPORTED_CURVE_NAME;

    private static final KeyFactory RSA_FACTORY = loadOrWarn("RSA");
    private static final KeyFactory DSA_FACTORY = loadOrWarn("DSA");
    private static final KeyFactory EC_FACTORY = loadOrWarn("EC");

    private static KeyFactory loadOrWarn(final String algorithm) {
        try {
            return KeyFactory.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            LOG.warn("KeyFactory for {} not found", algorithm, e);
            return null;
        }
    }

    private byte[] bytes = new byte[0];
    private int pos = 0;

    public PublicKey decodePublicKey(final String keyLine) throws GeneralSecurityException {

        // look for the Base64 encoded part of the line to decode
        // both ssh-rsa and ssh-dss begin with "AAAA" due to the length bytes
        bytes = Base64.getDecoder().decode(keyLine.getBytes(StandardCharsets.UTF_8));
        if (bytes.length == 0) {
            throw new IllegalArgumentException("No Base64 part to decode in " + keyLine);
        }

        pos = 0;

        final var type = decodeType();
        return switch (type) {
            case KEY_TYPE_RSA -> decodeAsRSA();
            case KEY_TYPE_DSA -> decodeAsDSA();
            case KEY_TYPE_ECDSA -> decodeAsEcDSA();
            default -> throw new IllegalArgumentException("Unknown decode key type " + type + " in " + keyLine);
        };
    }

    private PublicKey decodeAsEcDSA() throws GeneralSecurityException {
        if (EC_FACTORY == null) {
            throw new NoSuchAlgorithmException("ECDSA keys are not supported");
        }

        ECNamedCurveParameterSpec spec256r1 = ECNamedCurveTable.getParameterSpec(ECDSA_SUPPORTED_CURVE_NAME_SPEC);
        ECNamedCurveSpec params256r1 = new ECNamedCurveSpec(
            ECDSA_SUPPORTED_CURVE_NAME_SPEC, spec256r1.getCurve(), spec256r1.getG(), spec256r1.getN());
        // copy last 65 bytes from ssh key.
        ECPoint point = ECPointUtil.decodePoint(params256r1.getCurve(), Arrays.copyOfRange(bytes, 39, bytes.length));
        return EC_FACTORY.generatePublic(new ECPublicKeySpec(point, params256r1));
    }

    private PublicKey decodeAsDSA() throws GeneralSecurityException {
        if (DSA_FACTORY == null) {
            throw new NoSuchAlgorithmException("RSA keys are not supported");
        }

        BigInteger prime = decodeBigInt();
        BigInteger subPrime = decodeBigInt();
        BigInteger base = decodeBigInt();
        BigInteger publicKey = decodeBigInt();
        return DSA_FACTORY.generatePublic(new DSAPublicKeySpec(publicKey, prime, subPrime, base));
    }

    private PublicKey decodeAsRSA() throws GeneralSecurityException {
        if (RSA_FACTORY == null) {
            throw new NoSuchAlgorithmException("RSA keys are not supported");
        }

        BigInteger exponent = decodeBigInt();
        BigInteger modulus = decodeBigInt();
        return RSA_FACTORY.generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    private String decodeType() {
        int len = decodeInt();
        String type = new String(bytes, pos, len, StandardCharsets.UTF_8);
        pos += len;
        return type;
    }

    private int decodeInt() {
        return (bytes[pos++] & 0xFF) << 24 | (bytes[pos++] & 0xFF) << 16
                | (bytes[pos++] & 0xFF) << 8 | bytes[pos++] & 0xFF;
    }

    private BigInteger decodeBigInt() {
        int len = decodeInt();
        byte[] bigIntBytes = new byte[len];
        System.arraycopy(bytes, pos, bigIntBytes, 0, len);
        pos += len;
        return new BigInteger(bigIntBytes);
    }

    public static String encodePublicKey(final PublicKey publicKey) throws IOException {
        ByteArrayOutputStream byteOs = new ByteArrayOutputStream();
        if (publicKey instanceof RSAPublicKey rsa) {
            DataOutputStream dout = new DataOutputStream(byteOs);
            dout.writeInt(KEY_TYPE_RSA.getBytes(StandardCharsets.UTF_8).length);
            dout.write(KEY_TYPE_RSA.getBytes(StandardCharsets.UTF_8));
            dout.writeInt(rsa.getPublicExponent().toByteArray().length);
            dout.write(rsa.getPublicExponent().toByteArray());
            dout.writeInt(rsa.getModulus().toByteArray().length);
            dout.write(rsa.getModulus().toByteArray());
        } else if (publicKey instanceof DSAPublicKey dsa) {
            DSAParams dsaParams = dsa.getParams();
            DataOutputStream dout = new DataOutputStream(byteOs);
            dout.writeInt(KEY_TYPE_DSA.getBytes(StandardCharsets.UTF_8).length);
            dout.write(KEY_TYPE_DSA.getBytes(StandardCharsets.UTF_8));
            dout.writeInt(dsaParams.getP().toByteArray().length);
            dout.write(dsaParams.getP().toByteArray());
            dout.writeInt(dsaParams.getQ().toByteArray().length);
            dout.write(dsaParams.getQ().toByteArray());
            dout.writeInt(dsaParams.getG().toByteArray().length);
            dout.write(dsaParams.getG().toByteArray());
            dout.writeInt(dsa.getY().toByteArray().length);
            dout.write(dsa.getY().toByteArray());
        } else if (publicKey instanceof ECPublicKey ec) {
            DataOutputStream dout = new DataOutputStream(byteOs);
            dout.writeInt(KEY_TYPE_ECDSA.getBytes(StandardCharsets.UTF_8).length);
            dout.write(KEY_TYPE_ECDSA.getBytes(StandardCharsets.UTF_8));
            dout.writeInt(ECDSA_SUPPORTED_CURVE_NAME.getBytes(StandardCharsets.UTF_8).length);
            dout.write(ECDSA_SUPPORTED_CURVE_NAME.getBytes(StandardCharsets.UTF_8));

            byte[] coordX = ec.getQ().getAffineXCoord().getEncoded();
            byte[] coordY = ec.getQ().getAffineYCoord().getEncoded();
            dout.writeInt(coordX.length + coordY.length + 1);
            dout.writeByte(0x04);
            dout.write(coordX);
            dout.write(coordY);
        } else {
            throw new IllegalArgumentException("Unknown public key encoding: " + publicKey);
        }
        return Base64.getEncoder().encodeToString(byteOs.toByteArray());
    }
}
