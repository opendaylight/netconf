/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.EndEntityCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.PrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.PublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.TrustAnchorCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.asymmetric.key.pair.grouping._private.key.type.CleartextPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.LocalOrKeystoreAsymmetricKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.LocalOrKeystoreEndEntityCertWithKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev221212.tls.server.grouping.server.identity.auth.type.raw._private.key.RawPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.local.or.truststore.certs.grouping.LocalOrTruststore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.local.or.truststore.certs.grouping.local.or.truststore.local.local.definition.CertificateBuilder;

public final class TestUtils {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TestUtils() {
        // utility class
    }

    public static LocalOrTruststore buildLocalOrTruststore(Map<String, byte[]> certNameToBytesMap) {
        final var certMap = certNameToBytesMap.entrySet().stream()
                .map(entry -> new CertificateBuilder()
                        .setName(entry.getKey())
                        .setCertData(new TrustAnchorCertCms(entry.getValue()))
                        .build()
                ).collect(Collectors.toMap(cert -> cert.key(), cert -> cert));
        final var localDef = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212
                .local.or.truststore.certs.grouping.local.or.truststore.local.LocalDefinitionBuilder()
                .setCertificate(certMap).build();
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212
                .local.or.truststore.certs.grouping.local.or.truststore.LocalBuilder()
                .setLocalDefinition(localDef).build();
    }

    public static LocalOrKeystoreAsymmetricKeyGrouping buildAsymmetricKeyGrouping(
            final PublicKeyFormat publicKeyFormat, final byte[] publicKeyBytes,
            final PrivateKeyFormat privateKeyFormat, final byte[] privateKeyBytes) {
        final var localDef = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
                .local.or.keystore.asymmetric.key.grouping.local.or.keystore.local.LocalDefinitionBuilder()
                .setPublicKeyFormat(publicKeyFormat)
                .setPublicKey(publicKeyBytes)
                .setPrivateKeyFormat(privateKeyFormat)
                .setPrivateKeyType(new CleartextPrivateKeyBuilder().setCleartextPrivateKey(privateKeyBytes).build())
                .build();
        return new RawPrivateKeyBuilder()
                .setLocalOrKeystore(
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
                                .local.or.keystore.asymmetric.key.grouping.local.or.keystore.LocalBuilder()
                                .setLocalDefinition(localDef).build())
                .build();
    }

    public static LocalOrKeystoreEndEntityCertWithKeyGrouping buildEndEntityCertWithKeyGrouping(
            final PublicKeyFormat publicKeyFormat, final byte[] publicKeyBytes,
            final PrivateKeyFormat privateKeyFormat, final byte[] privateKeyBytes, final byte[] certificateBytes) {
        final var localDef = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
                .local.or.keystore.end.entity.cert.with.key.grouping.local.or.keystore.local.LocalDefinitionBuilder()
                .setPublicKeyFormat(publicKeyFormat)
                .setPublicKey(publicKeyBytes)
                .setPrivateKeyFormat(privateKeyFormat)
                .setPrivateKeyType(new CleartextPrivateKeyBuilder().setCleartextPrivateKey(privateKeyBytes).build())
                .setCertData(new EndEntityCertCms(certificateBytes))
                .build();
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev221212
                .tls.server.grouping.server.identity.auth.type.certificate.CertificateBuilder()
                .setLocalOrKeystore(
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
                                .local.or.keystore.end.entity.cert.with.key.grouping.local.or.keystore.LocalBuilder()
                                .setLocalDefinition(localDef).build())
                .build();
    }

    public static X509CertData generateX509CertData(final String algorithm) throws Exception {
        final var keyPairGenerator = KeyPairGenerator.getInstance(algorithm);
        if (isRSA(algorithm)) {
            keyPairGenerator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4), SECURE_RANDOM);
        } else {
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"), SECURE_RANDOM);
        }
        final var keyPair = keyPairGenerator.generateKeyPair();
        final var certificate = generateCertificate(keyPair, isRSA(algorithm) ? "SHA256withRSA" : "SHA256withECDSA");
        final var publicKeyBytes = keyPair.getPublic().getEncoded();
        final var privateKeyBytes = keyPair.getPrivate().getEncoded();
        return new X509CertData(certificate.getEncoded(), publicKeyBytes, privateKeyBytes,
                OpenSSHPublicKeyUtil.encodePublicKey(PublicKeyFactory.createKey(publicKeyBytes)));
    }

    private static X509Certificate generateCertificate(final KeyPair keyPair, final String hashAlgorithm)
            throws Exception {
        final var now = Instant.now();
        final var contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate());

        final var x500Name = new X500Name("CN=TestCertificate");
        final var certificateBuilder = new JcaX509v3CertificateBuilder(x500Name,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now), Date.from(now.plus(Duration.ofDays(365))),
                x500Name,
                keyPair.getPublic());
        return new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
    }

    public static boolean isRSA(final String algorithm) {
        return KeyUtils.RSA_ALGORITHM.equals(algorithm);
    }

    public record X509CertData(byte[] certBytes, byte[] publicKey, byte[] privateKey, byte[] sshPublicKey) {
    }
}
