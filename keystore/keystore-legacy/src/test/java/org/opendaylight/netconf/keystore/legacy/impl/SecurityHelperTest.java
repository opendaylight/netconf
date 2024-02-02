/*
 * Copyright (c) 2017 Brocade Communication Systems and others.  All rights reserved.
 * Copyright (c) 2024 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import org.bouncycastle.openssl.EncryptionException;
import org.junit.jupiter.api.Test;

class SecurityHelperTest {
    private final SecurityHelper helper = new SecurityHelper();

    @Test
    void testRSAKey() throws Exception {
        assertNotNull(decodePrivateKey("rsa", ""));
    }

    @Test
    void testRSAEncryptedKey() throws Exception {
        assertNotNull(decodePrivateKey("rsa_encrypted", "passphrase"));
    }

    @Test
    void testRSAWrongPassphrase() {
        final var ex = assertThrows(EncryptionException.class, () -> decodePrivateKey("rsa_encrypted", "wrong"));
        assertEquals("exception using cipher - please check password and data.", ex.getMessage());
    }

    @Test
    void testDSAKey() throws Exception {
        assertNotNull(decodePrivateKey("dsa", ""));
    }

    @Test
    void testDSAEncryptedKey() throws Exception {
        assertNotNull(decodePrivateKey("dsa_encrypted", "passphrase"));
    }

    @Test
    void testDSAWrongPassphrase() {
        final var ex = assertThrows(EncryptionException.class, () -> decodePrivateKey("dsa_encrypted", "wrong"));
        assertEquals("exception using cipher - please check password and data.", ex.getMessage());
    }

    @Test
    @SuppressWarnings("AbbreviationAsWordInName")
    void testECDSAKey() throws Exception {
        assertNotNull(decodePrivateKey("ecdsa", ""));
    }

    @Test
    @SuppressWarnings("AbbreviationAsWordInName")
    void testECDSAEncryptedKey() throws Exception {
        assertNotNull(decodePrivateKey("ecdsa_encrypted", "passphrase"));
    }

    @Test
    @SuppressWarnings("AbbreviationAsWordInName")
    void testECDSAWrongPassphrase() {
        final var ex = assertThrows(EncryptionException.class, () -> decodePrivateKey("ecdsa_encrypted", "wrong"));
        assertEquals("exception using cipher - please check password and data.", ex.getMessage());
    }

    private KeyPair decodePrivateKey(final String resourceName, final String password) throws Exception {
        return helper.decodePrivateKey(
            new String(SecurityHelperTest.class.getResourceAsStream("/pki/" + resourceName).readAllBytes(),
                StandardCharsets.UTF_8),
            password);
    }
}
