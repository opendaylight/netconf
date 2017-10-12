/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
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
import java.util.HashMap;
import java.util.Map;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.ECPointUtil;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;

/**
 * FIXME: This should be probably located at AAA library.
 */
public class AuthorizedKeysDecoder {

    private static final String KEY_FACTORY_TYPE_RSA = "RSA";
    private static final String KEY_FACTORY_TYPE_DSA = "DSA";
    private static final String KEY_FACTORY_TYPE_ECDSA = "EC";

    private static final Map<String, String> ECDSA_CURVES = new HashMap<>();

    static {
        ECDSA_CURVES.put("nistp256", "secp256r1");
        ECDSA_CURVES.put("nistp384", "secp384r1");
        ECDSA_CURVES.put("nistp512", "secp512r1");
    }

    private static final String ECDSA_SUPPORTED_CURVE_NAME = "nistp256";
    private static final String ECDSA_SUPPORTED_CURVE_NAME_SPEC = ECDSA_CURVES.get(ECDSA_SUPPORTED_CURVE_NAME);

    private static final String KEY_TYPE_RSA = "ssh-rsa";
    private static final String KEY_TYPE_DSA = "ssh-dss";
    private static final String KEY_TYPE_ECDSA = "ecdsa-sha2-" + ECDSA_SUPPORTED_CURVE_NAME;

    private byte[] bytes = new byte[0];
    private int pos = 0;

    public PublicKey decodePublicKey(final String keyLine) throws GeneralSecurityException {

        // look for the Base64 encoded part of the line to decode
        // both ssh-rsa and ssh-dss begin with "AAAA" due to the length bytes
        bytes = Base64.getDecoder().decode(keyLine.getBytes());
        if (bytes.length == 0) {
            throw new IllegalArgumentException("No Base64 part to decode in " + keyLine);
        }

        pos = 0;

        String type = decodeType();
        if (type.equals(KEY_TYPE_RSA)) {
            return decodeAsRSA();
        }

        if (type.equals(KEY_TYPE_DSA)) {
            return decodeAsDSA();
        }

        if (type.equals(KEY_TYPE_ECDSA)) {
            return decodeAsEcDSA();
        }

        throw new IllegalArgumentException("Unknown decode key type " + type + " in " + keyLine);
    }

    private PublicKey decodeAsEcDSA() throws GeneralSecurityException {
        KeyFactory ecdsaFactory = SecurityUtils.getKeyFactory(KEY_FACTORY_TYPE_ECDSA);

        ECNamedCurveParameterSpec spec256r1 = ECNamedCurveTable.getParameterSpec(ECDSA_SUPPORTED_CURVE_NAME_SPEC);
        ECNamedCurveSpec params256r1 = new ECNamedCurveSpec(
            ECDSA_SUPPORTED_CURVE_NAME_SPEC, spec256r1.getCurve(), spec256r1.getG(), spec256r1.getN());
        // copy last 65 bytes from ssh key.
        ECPoint point = ECPointUtil.decodePoint(params256r1.getCurve(), Arrays.copyOfRange(bytes, 39, bytes.length));
        ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(point, params256r1);

        return ecdsaFactory.generatePublic(pubKeySpec);
    }

    private PublicKey decodeAsDSA() throws GeneralSecurityException {
        KeyFactory dsaFactory = SecurityUtils.getKeyFactory(KEY_FACTORY_TYPE_DSA);
        BigInteger prime = decodeBigInt();
        BigInteger subPrime = decodeBigInt();
        BigInteger base = decodeBigInt();
        BigInteger publicKey = decodeBigInt();
        DSAPublicKeySpec spec = new DSAPublicKeySpec(publicKey, prime, subPrime, base);

        return dsaFactory.generatePublic(spec);
    }

    private PublicKey decodeAsRSA() throws GeneralSecurityException {
        KeyFactory rsaFactory = SecurityUtils.getKeyFactory(KEY_FACTORY_TYPE_RSA);
        BigInteger exponent = decodeBigInt();
        BigInteger modulus = decodeBigInt();
        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);

        return rsaFactory.generatePublic(spec);
    }

    private String decodeType() {
        int len = decodeInt();
        String type = new String(bytes, pos, len);
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
        String publicKeyEncoded;
        ByteArrayOutputStream byteOs = new ByteArrayOutputStream();
        if (publicKey.getAlgorithm().equals(KEY_FACTORY_TYPE_RSA)) {
            RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
            DataOutputStream dout = new DataOutputStream(byteOs);
            dout.writeInt(KEY_TYPE_RSA.getBytes().length);
            dout.write(KEY_TYPE_RSA.getBytes());
            dout.writeInt(rsaPublicKey.getPublicExponent().toByteArray().length);
            dout.write(rsaPublicKey.getPublicExponent().toByteArray());
            dout.writeInt(rsaPublicKey.getModulus().toByteArray().length);
            dout.write(rsaPublicKey.getModulus().toByteArray());
        } else if (publicKey.getAlgorithm().equals(KEY_FACTORY_TYPE_DSA)) {
            DSAPublicKey dsaPublicKey = (DSAPublicKey) publicKey;
            DSAParams dsaParams = dsaPublicKey.getParams();
            DataOutputStream dout = new DataOutputStream(byteOs);
            dout.writeInt(KEY_TYPE_DSA.getBytes().length);
            dout.write(KEY_TYPE_DSA.getBytes());
            dout.writeInt(dsaParams.getP().toByteArray().length);
            dout.write(dsaParams.getP().toByteArray());
            dout.writeInt(dsaParams.getQ().toByteArray().length);
            dout.write(dsaParams.getQ().toByteArray());
            dout.writeInt(dsaParams.getG().toByteArray().length);
            dout.write(dsaParams.getG().toByteArray());
            dout.writeInt(dsaPublicKey.getY().toByteArray().length);
            dout.write(dsaPublicKey.getY().toByteArray());
        } else if (publicKey.getAlgorithm().equals(KEY_FACTORY_TYPE_ECDSA)) {
            BCECPublicKey ecPublicKey = (BCECPublicKey) publicKey;
            DataOutputStream dout = new DataOutputStream(byteOs);
            dout.writeInt(KEY_TYPE_ECDSA.getBytes().length);
            dout.write(KEY_TYPE_ECDSA.getBytes());
            dout.writeInt(ECDSA_SUPPORTED_CURVE_NAME.getBytes().length);
            dout.write(ECDSA_SUPPORTED_CURVE_NAME.getBytes());

            byte[] coordX = ecPublicKey.getQ().getAffineXCoord().getEncoded();
            byte[] coordY = ecPublicKey.getQ().getAffineYCoord().getEncoded();
            dout.writeInt(coordX.length + coordY.length + 1);
            dout.writeByte(0x04);
            dout.write(coordX);
            dout.write(coordY);
        } else {
            throw new IllegalArgumentException("Unknown public key encoding: " + publicKey.getAlgorithm());
        }
        publicKeyEncoded = new String(Base64.getEncoder().encodeToString(byteOs.toByteArray()));
        return publicKeyEncoded;
    }
}
