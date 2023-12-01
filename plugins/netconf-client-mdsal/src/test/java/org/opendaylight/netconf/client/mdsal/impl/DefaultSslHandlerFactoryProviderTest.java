/*
 * Copyright (c) 2018 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStoreException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificateBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;

@ExtendWith(MockitoExtension.class)
class DefaultSslHandlerFactoryProviderTest {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final String INVALID_DATA = encodeBase64("invalid.data".getBytes(StandardCharsets.UTF_8));

    @Mock
    private DataBroker dataBroker;
    @Mock
    private ListenerRegistration<?> listenerRegistration;

    @BeforeEach
    void beforeEach() {
        doReturn(listenerRegistration).when(dataBroker)
            .registerDataTreeChangeListener(any(DataTreeIdentifier.class), any(DefaultSslHandlerFactoryProvider.class));
    }

    @Test
    void getJavaKeyStore() {
        final var keystoreAdapter = new DefaultSslHandlerFactoryProvider(dataBroker);
        final var ex = assertThrows(KeyStoreException.class, keystoreAdapter::getJavaKeyStore);
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().startsWith("No keystore private key found"));
    }

    @Test
    void getJavaKeyStoreWithAliases() throws Exception {
        final var validPrivateKey = generatePrivateKey("valid-private-key");
        final var invalidPrivateKey = new PrivateKeyBuilder()
            .setName("invalid-private-key").setData(INVALID_DATA).build();

        final var keystoreAdapter = new DefaultSslHandlerFactoryProvider(dataBroker);
        keystoreAdapter.onDataTreeChanged(List.of(
            mockModification(PrivateKey.class, validPrivateKey),
            mockModification(PrivateKey.class, invalidPrivateKey)
        ));
        final var keyStore = keystoreAdapter.getJavaKeyStore(Set.of("valid-private-key"));
        assertTrue(keyStore.containsAlias("valid-private-key"));

        final var ex = assertThrows(KeyStoreException.class,
            () -> keystoreAdapter.getJavaKeyStore(Set.of("invalid-private-key", "unknown-alias")));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().startsWith("None of allowed private keys found."));
    }

    @Test
    void writePrivateKeyAndTrustedCertificate() throws Exception {
        final var privateKey = generatePrivateKey("test-private-key");
        final var trustedCertificate = generateTrustedCertificate("test-trusted-cert");

        // Apply configurations
        final var keystoreAdapter = new DefaultSslHandlerFactoryProvider(dataBroker);
        keystoreAdapter.onDataTreeChanged(List.of(
            mockModification(PrivateKey.class, privateKey),
            mockModification(TrustedCertificate.class, trustedCertificate)));

        // Check result
        final var keyStore = keystoreAdapter.getJavaKeyStore();
        assertTrue(keyStore.containsAlias(privateKey.getName()));
        assertTrue(keyStore.containsAlias(trustedCertificate.getName()));
    }

    @Test
    void invalidCertificatesIgnored() throws Exception {
        final var validPrivateKey = generatePrivateKey("valid-private-key");
        final var invalidPrivateKey1 = new PrivateKeyBuilder()
            .setName("invalid-private-key-1")
            .setData(INVALID_DATA)
            .setCertificateChain(validPrivateKey.getCertificateChain())
            .build();
        final var invalidPrivateKey2 = new PrivateKeyBuilder()
            .setName("invalid-private-key-2")
            .setData(validPrivateKey.getData())
            .setCertificateChain(List.of(INVALID_DATA)).build();

        final var validTrustedCertificate = generateTrustedCertificate("valid-trusted-cert");
        final var invalidTrustedCertificate = new TrustedCertificateBuilder()
            .setName("invalid-trusted-cert").setCertificate(INVALID_DATA).build();

        // Apply configurations
        final var keystoreAdapter = new DefaultSslHandlerFactoryProvider(dataBroker);
        keystoreAdapter.onDataTreeChanged(List.of(
            mockModification(PrivateKey.class, validPrivateKey),
            mockModification(PrivateKey.class, invalidPrivateKey1),
            mockModification(PrivateKey.class, invalidPrivateKey2),
            mockModification(TrustedCertificate.class, validTrustedCertificate),
            mockModification(TrustedCertificate.class, invalidTrustedCertificate)));

        // Check result
        final var keyStore = keystoreAdapter.getJavaKeyStore();
        assertTrue(keyStore.containsAlias(validPrivateKey.getName()));
        assertFalse(keyStore.containsAlias(invalidPrivateKey1.getName()));
        assertFalse(keyStore.containsAlias(invalidPrivateKey2.getName()));

        assertTrue(keyStore.containsAlias(validTrustedCertificate.getName()));
        assertFalse(keyStore.containsAlias(invalidTrustedCertificate.getName()));
    }

    private static <T extends DataObject> DataTreeModification<Keystore> mockModification(final Class<T> type,
            final T modifiedObj) {

        final DataTreeModification<Keystore> dataTreeModification = mock(DataTreeModification.class);
        final DataObjectModification<Keystore> dataObjectModification = mock(DataObjectModification.class);
        doReturn(dataObjectModification).when(dataTreeModification).getRootNode();

        final DataObjectModification<T> childObjectModification = mock(DataObjectModification.class);
        doReturn(List.of(childObjectModification)).when(dataObjectModification).getModifiedChildren();

        doReturn(DataObjectModification.ModificationType.WRITE).when(childObjectModification).getModificationType();
        doReturn(type).when(childObjectModification).getDataType();
        doReturn(modifiedObj).when(childObjectModification).getDataAfter();

        return dataTreeModification;
    }

    private static PrivateKey generatePrivateKey(final String name) throws Exception {
        final var certData = generateCertData();
        return new PrivateKeyBuilder()
            .setName(name)
            .setData(encodeBase64(certData.keyPair().getPrivate().getEncoded()))
            .setCertificateChain(List.of(encodeBase64(certData.certificate().getEncoded())))
            .build();
    }

    private static TrustedCertificate generateTrustedCertificate(final String name) throws Exception {
        final var certData = generateCertData();
        return new TrustedCertificateBuilder()
            .setName(name)
            .setCertificate(encodeBase64(certData.certificate().getEncoded()))
            .build();
    }

    private static String encodeBase64(final byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static CertData generateCertData() throws Exception {
        // key pair
        final var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4), new SecureRandom());
        final var keyPair = keyPairGenerator.generateKeyPair();
        // certificate
        final var now = Instant.now();
        final var contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        final var x500Name = new X500Name("CN=TestCertificate" + COUNTER.incrementAndGet());
        final var certificateBuilder = new JcaX509v3CertificateBuilder(x500Name,
            BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now), Date.from(now.plus(Duration.ofDays(365))),
            x500Name,
            keyPair.getPublic());
        final var certificate = new JcaX509CertificateConverter()
            .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
        return new CertData(keyPair, certificate);
    }

    private record CertData(KeyPair keyPair, Certificate certificate) {
    }
}
