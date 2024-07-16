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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import org.bouncycastle.openssl.EncryptionException;
import org.junit.jupiter.api.Test;

//TODO: Rework to parameterized test
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

    @Test
    void testCertificate() throws Exception {
        assertNotNull(decodeCertificate("certificate"));
    }

    @Test
    void testCertificateWithPrivateKeyInput() {
        final var ex = assertThrows(IOException.class, () -> SecurityHelper.decodeCertificate(
            new String(SecurityHelperTest.class.getResourceAsStream("/pem/rsa").readAllBytes(),
                StandardCharsets.UTF_8)));
        assertEquals("Unhandled certificate class org.bouncycastle.openssl.PEMKeyPair", ex.getMessage());
    }

    @Test
    void testGenerateKeyPair() throws Exception {
        final var keyPair = decodePrivateKey("rsa", "");
        assertInstanceOf(KeyPair.class, SecurityHelper.generateKeyPair(keyPair.getPrivate().getEncoded(),
            keyPair.getPublic().getEncoded(), keyPair.getPrivate().getAlgorithm()));
    }

    @Test
    void testGeneratePrivateKey() throws Exception {
        final var keyPair = decodePrivateKey("rsa", "");
        assertInstanceOf(PrivateKey.class, SecurityHelper.generatePrivateKey(keyPair.getPrivate().getEncoded(),
            keyPair.getPrivate().getAlgorithm()));
    }

    @Test
    void testGenerateCertificate() throws Exception {
        final var cert = decodeCertificate("certificate");
        assertNotNull(helper.generateCertificate(cert.getEncoded()));
    }

    private KeyPair decodePrivateKey(final String resourceName, final String password) throws Exception {
        return helper.decodePrivateKey(
            new String(SecurityHelperTest.class.getResourceAsStream("/pem/" + resourceName).readAllBytes(),
                StandardCharsets.UTF_8),
            password);
    }

    private static X509Certificate decodeCertificate(final String resourceName) throws Exception {
        return SecurityHelper.decodeCertificate(
            new String(SecurityHelperTest.class.getResourceAsStream("/pem/" + resourceName).readAllBytes(),
                StandardCharsets.UTF_8));
    }
}
