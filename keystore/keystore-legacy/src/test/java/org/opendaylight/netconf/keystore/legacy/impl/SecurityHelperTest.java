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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SecurityHelperTest {
    private final SecurityHelper helper = new SecurityHelper();

    @ParameterizedTest
    @CsvSource({
        "rsa, ''",
        "rsa_encrypted, 'passphrase'",
        "dsa, ''",
        "dsa_encrypted, 'passphrase'",
        "ecdsa, ''",
        "ecdsa_encrypted, 'passphrase'"
    })
    void testDecodePrivateKey(String resourceName, String password) throws Exception {
        assertNotNull(decodePrivateKey(resourceName, password));
    }

    @ParameterizedTest
    @CsvSource({
        "rsa_encrypted, 'wrong'",
        "dsa_encrypted, 'wrong'",
        "ecdsa_encrypted, 'wrong'"
    })
    void testWrongPassphrase(String resourceName, String password) {
        assertThrows(EncryptionException.class, () -> decodePrivateKey(resourceName, password));
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
