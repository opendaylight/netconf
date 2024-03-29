/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.ECPointUtil;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.SshPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AuthorizedKeysDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorizedKeysDecoder.class);

    private static final ImmutableMap<String, String> ECDSA_CURVES = ImmutableMap.<String, String>builder()
        .put("nistp256", "secp256r1")
        .put("nistp384", "secp384r1")
        .put("nistp512", "secp512r1")
        .build();

    private static final String ECDSA_SUPPORTED_CURVE_NAME = "nistp256";
    private static final byte[] ECDSA_SUPPORTED_CURVE_NAME_BYTES =
        ECDSA_SUPPORTED_CURVE_NAME.getBytes(StandardCharsets.UTF_8);
    private static final String ECDSA_SUPPORTED_CURVE_NAME_SPEC = ECDSA_CURVES.get(ECDSA_SUPPORTED_CURVE_NAME);

    private static final String KEY_TYPE_RSA = "ssh-rsa";
    private static final byte[] KEY_TYPE_RSA_BYTES = KEY_TYPE_RSA.getBytes(StandardCharsets.UTF_8);

    private static final String KEY_TYPE_DSA = "ssh-dss";
    private static final byte[] KEY_TYPE_DSA_BYTES = KEY_TYPE_DSA.getBytes(StandardCharsets.UTF_8);
    private static final String KEY_TYPE_ECDSA = "ecdsa-sha2-" + ECDSA_SUPPORTED_CURVE_NAME;
    private static final byte[] KEY_TYPE_ECDSA_BYTES = KEY_TYPE_ECDSA.getBytes(StandardCharsets.UTF_8);

    private static final KeyFactory RSA_FACTORY;
    private static final KeyFactory DSA_FACTORY;
    private static final KeyFactory EC_FACTORY;

    static {
        // Default provider fails to validate keys, see https://jira.opendaylight.org/browse/NETCONF-1249
        var bcprov = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (bcprov == null) {
            bcprov = new BouncyCastleProvider();
        }
        RSA_FACTORY = loadOrWarn("RSA", bcprov);
        DSA_FACTORY = loadOrWarn("DSA", bcprov);
        EC_FACTORY = loadOrWarn("EC", bcprov);
    }

    private static KeyFactory loadOrWarn(final String algorithm, final Provider provider) {
        try {
            return KeyFactory.getInstance(algorithm, provider);
        } catch (NoSuchAlgorithmException e) {
            LOG.warn("KeyFactory for {} not found", algorithm, e);
            return null;
        }
    }

    private final byte[] bytes;
    private int pos;

    private AuthorizedKeysDecoder(final SshPublicKey keyLine) {
        bytes = keyLine.getValue();
    }

    static @NonNull PublicKey decodePublicKey(final @NonNull SshPublicKey keyLine) throws GeneralSecurityException {
        final var instance = new AuthorizedKeysDecoder(keyLine);
        final var type = instance.decodeType();
        return switch (type) {
            case KEY_TYPE_RSA -> instance.decodeAsRSA();
            case KEY_TYPE_DSA -> instance.decodeAsDSA();
            case KEY_TYPE_ECDSA -> instance.decodeAsEcDSA();
            default -> throw new NoSuchAlgorithmException("Unknown decode key type " + type + " in " + keyLine);
        };
    }

    static @NonNull SshPublicKey encodePublicKey(final PublicKey publicKey) throws IOException {
        final var baos = new ByteArrayOutputStream();

        try (var dout = new DataOutputStream(baos)) {
            if (publicKey instanceof RSAPublicKey rsa) {
                dout.writeInt(KEY_TYPE_RSA_BYTES.length);
                dout.write(KEY_TYPE_RSA_BYTES);
                encodeBigInt(dout, rsa.getPublicExponent());
                encodeBigInt(dout, rsa.getModulus());
            } else if (publicKey instanceof DSAPublicKey dsa) {
                final var dsaParams = dsa.getParams();
                dout.writeInt(KEY_TYPE_DSA_BYTES.length);
                dout.write(KEY_TYPE_DSA_BYTES);
                encodeBigInt(dout, dsaParams.getP());
                encodeBigInt(dout, dsaParams.getQ());
                encodeBigInt(dout, dsaParams.getG());
                encodeBigInt(dout, dsa.getY());
            } else if (publicKey instanceof ECPublicKey ec) {
                dout.writeInt(KEY_TYPE_ECDSA_BYTES.length);
                dout.write(KEY_TYPE_ECDSA_BYTES);
                dout.writeInt(ECDSA_SUPPORTED_CURVE_NAME_BYTES.length);
                dout.write(ECDSA_SUPPORTED_CURVE_NAME_BYTES);

                final var q = ec.getQ();
                final var coordX = q.getAffineXCoord().getEncoded();
                final var coordY = q.getAffineYCoord().getEncoded();
                dout.writeInt(coordX.length + coordY.length + 1);
                dout.writeByte(0x04);
                dout.write(coordX);
                dout.write(coordY);
            } else {
                throw new IOException("Unknown public key encoding: " + publicKey);
            }
        }
        return new SshPublicKey(baos.toByteArray());
    }

    private @NonNull PublicKey decodeAsEcDSA() throws GeneralSecurityException {
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

    private @NonNull PublicKey decodeAsDSA() throws GeneralSecurityException {
        if (DSA_FACTORY == null) {
            throw new NoSuchAlgorithmException("RSA keys are not supported");
        }

        final var p = decodeBigInt();
        final var q = decodeBigInt();
        final var g = decodeBigInt();
        final var y = decodeBigInt();
        return DSA_FACTORY.generatePublic(new DSAPublicKeySpec(y, p, q, g));
    }

    private @NonNull PublicKey decodeAsRSA() throws GeneralSecurityException {
        if (RSA_FACTORY == null) {
            throw new NoSuchAlgorithmException("RSA keys are not supported");
        }

        final var exponent = decodeBigInt();
        final var modulus = decodeBigInt();
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

    private static void encodeBigInt(final DataOutput out, final BigInteger value) throws IOException {
        final var bytes = value.toByteArray();
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
