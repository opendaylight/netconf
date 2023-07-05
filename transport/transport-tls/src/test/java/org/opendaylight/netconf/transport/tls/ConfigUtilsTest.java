/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendaylight.netconf.transport.tls.ConfigUtils.DEFAULT_CERTIFICATE_ALIAS;
import static org.opendaylight.netconf.transport.tls.ConfigUtils.DEFAULT_PRIVATE_KEY_ALIAS;
import static org.opendaylight.netconf.transport.tls.ConfigUtils.EMPTY_SECRET;
import static org.opendaylight.netconf.transport.tls.TestUtils.buildAsymmetricKeyGrouping;
import static org.opendaylight.netconf.transport.tls.TestUtils.buildEndEntityCertWithKeyGrouping;
import static org.opendaylight.netconf.transport.tls.TestUtils.buildInlineOrTruststore;
import static org.opendaylight.netconf.transport.tls.TestUtils.generateX509CertData;

import java.security.KeyStore;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.PrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.PublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.SshPublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417.tls.client.grouping.server.authentication.CaCertsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417.tls.client.grouping.server.authentication.EeCertsBuilder;

class ConfigUtilsTest {

    private KeyStore keyStore;

    @BeforeEach
    void setup() throws Exception {
        keyStore = KeyStoreUtils.newKeyStore();
    }

    @Test
    void setX509Certificates() throws Exception {
        final var rsaCertData = generateX509CertData("RSA");
        final var ecCertData = generateX509CertData("EC");

        // not defined -- ignore
        ConfigUtils.setX509Certificates(keyStore, null, null);
        assertFalse(keyStore.aliases().hasMoreElements());

        // defined
        final var inlineOrTruststore = buildInlineOrTruststore(
                Map.of("cert-rsa", rsaCertData.certBytes(), "cert-ec", ecCertData.certBytes()));
        final var caCerts = new CaCertsBuilder().setInlineOrTruststore(inlineOrTruststore).build();
        final var eeCerts = new EeCertsBuilder().setInlineOrTruststore(inlineOrTruststore).build();
        ConfigUtils.setX509Certificates(keyStore, caCerts, eeCerts);

        final List<String> aliases = Collections.list(keyStore.aliases());
        final Set<String> expectedAliases = Set.of("ca-cert-rsa", "ca-cert-ec", "ee-cert-rsa", "ee-cert-ec");
        assertNotNull(aliases);
        assertEquals(4, aliases.size());
        assertEquals(expectedAliases, Set.copyOf(aliases));
        for (String alias : aliases) {
            assertTrue(keyStore.isCertificateEntry(alias));
            assertNotNull(keyStore.getCertificate(alias));
        }
    }

    @ParameterizedTest(name = "Set private key from pair: {0}")
    @MethodSource("asymmetricKeyArgs")
    @Disabled
        // raw public key is not implemented yet
    void setPrivateKeyFromKeyPair(final String testDesc,
            final PublicKeyFormat publicKeyFormat, final byte[] publicKeyBytes,
            final PrivateKeyFormat privateKeyFormat, final byte[] privateKeyBytes,
            final byte[] certificateBytes) throws Exception {
        final var rawPrivateKey = buildAsymmetricKeyGrouping(publicKeyFormat, publicKeyBytes,
                privateKeyFormat, privateKeyBytes);
        ConfigUtils.setAsymmetricKey(keyStore, rawPrivateKey);
        assertTrue(keyStore.containsAlias(DEFAULT_PRIVATE_KEY_ALIAS));
        assertTrue(keyStore.isKeyEntry(DEFAULT_PRIVATE_KEY_ALIAS));
        assertNotNull(keyStore.getKey(DEFAULT_PRIVATE_KEY_ALIAS, EMPTY_SECRET));
    }

    @ParameterizedTest(name = "End entity certificate: {0}")
    @MethodSource("asymmetricKeyArgs")
    void setEndEntityCertificateWithKey(final String testDesc,
            final PublicKeyFormat publicKeyFormat, final byte[] publicKeyBytes,
            final PrivateKeyFormat privateKeyFormat, final byte[] privateKeyBytes,
            final byte[] certificateBytes) throws Exception {
        final var endEntityCert = buildEndEntityCertWithKeyGrouping(publicKeyFormat, publicKeyBytes,
                privateKeyFormat, privateKeyBytes, certificateBytes);
        ConfigUtils.setEndEntityCertificateWithKey(keyStore, endEntityCert);

        assertTrue(keyStore.containsAlias(DEFAULT_PRIVATE_KEY_ALIAS));
        assertTrue(keyStore.isKeyEntry(DEFAULT_PRIVATE_KEY_ALIAS));
        assertNotNull(keyStore.getKey(DEFAULT_PRIVATE_KEY_ALIAS, EMPTY_SECRET));

        assertTrue(keyStore.containsAlias(DEFAULT_CERTIFICATE_ALIAS));
        assertTrue(keyStore.isCertificateEntry(DEFAULT_CERTIFICATE_ALIAS));
        assertNotNull(keyStore.getCertificate(DEFAULT_CERTIFICATE_ALIAS));
    }

    private static Stream<Arguments> asymmetricKeyArgs() throws Exception {
        // (test case descriptor, public key format, public key bytes, private key format, private key bytes,
        // certificate bytes)
        final var rsaCertData = generateX509CertData("RSA");
        final var ecCertData = generateX509CertData("EC");
        return Stream.of(
                Arguments.of("RSA / subject-public-key-info",
                        SubjectPublicKeyInfoFormat.VALUE, rsaCertData.publicKey(),
                        RsaPrivateKeyFormat.VALUE, rsaCertData.privateKey(),
                        rsaCertData.certBytes()),
                Arguments.of("RSA / ssh-public-key",
                        SshPublicKeyFormat.VALUE, rsaCertData.sshPublicKey(),
                        RsaPrivateKeyFormat.VALUE, rsaCertData.privateKey(),
                        rsaCertData.certBytes()),
                Arguments.of("EC / subject-public-key-info",
                        SubjectPublicKeyInfoFormat.VALUE, ecCertData.publicKey(),
                        EcPrivateKeyFormat.VALUE, ecCertData.privateKey(),
                        ecCertData.certBytes()),
                Arguments.of("EC / ssh-public-key",
                        SshPublicKeyFormat.VALUE, ecCertData.sshPublicKey(),
                        EcPrivateKeyFormat.VALUE, ecCertData.privateKey(),
                        ecCertData.certBytes())
        );
    }
}
