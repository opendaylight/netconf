/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.ssh;

import com.google.common.collect.Sets;
import java.io.File;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Scanner;
import java.util.Set;
import javax.xml.bind.DatatypeConverter;

/**
 * Parse and decode public keys from authorized_key file. Supported algorithms: rsa, dsa.
 */
public class AuthorizedKeysDecoder {

    private byte[] bytes;
    private int pos;

    public final Set<PublicKey> parseAuthorizedKeys(final String authorizedKeyPaths) throws Exception {
        final Set<PublicKey> parsedPublicKeys = Sets.newHashSet();
        final AuthorizedKeysDecoder decoder = new AuthorizedKeysDecoder();
        final File file = new File(authorizedKeyPaths);
        final Scanner scanner = new Scanner(file).useDelimiter("\n");
        while (scanner.hasNext()) {
            parsedPublicKeys.add(decoder.decodePublicKeyLine(scanner.next()));
        }
        scanner.close();

        return parsedPublicKeys;
    }

    private PublicKey decodePublicKeyLine(final String keyLine) throws Exception {
        bytes = null;
        pos = 0;

        // look for the Base64 encoded part of the line to decode
        // both ssh-rsa and ssh-dss begin with "AAAA" due to the length bytes
        for (String part : keyLine.split(" ")) {
            if (part.startsWith("AAAA")) {
                bytes = DatatypeConverter.parseBase64Binary(part);
                break;
            }
        }
        if (bytes == null) {
            throw new IllegalArgumentException("no Base64 part to decode");
        }

        final String type = decodeType();
        switch (type) {
            case "ssh-rsa": {
                final BigInteger e = decodeBigInt();
                final BigInteger m = decodeBigInt();
                final RSAPublicKeySpec spec = new RSAPublicKeySpec(m, e);
                return KeyFactory.getInstance("RSA").generatePublic(spec);
            }
            case "ssh-dss": {
                final BigInteger p = decodeBigInt();
                final BigInteger q = decodeBigInt();
                final BigInteger g = decodeBigInt();
                final BigInteger y = decodeBigInt();
                final DSAPublicKeySpec spec = new DSAPublicKeySpec(y, p, q, g);
                return KeyFactory.getInstance("DSA").generatePublic(spec);
            }
            default:
                throw new IllegalArgumentException("unknown type " + type);
        }
    }

    private String decodeType() {
        final int len = decodeInt();
        final String type = new String(bytes, pos, len);
        pos += len;
        return type;
    }

    private int decodeInt() {
        return ((bytes[pos++] & 0xFF) << 24) | ((bytes[pos++] & 0xFF) << 16)
                | ((bytes[pos++] & 0xFF) << 8) | (bytes[pos++] & 0xFF);
    }

    private BigInteger decodeBigInt() {
        final int len = decodeInt();
        final byte[] bigIntBytes = new byte[len];
        System.arraycopy(bytes, pos, bigIntBytes, 0, len);
        pos += len;
        return new BigInteger(bigIntBytes);
    }

}
