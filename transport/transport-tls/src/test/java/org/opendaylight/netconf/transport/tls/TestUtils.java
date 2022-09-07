/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static java.util.stream.Collectors.toMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.bouncycastle.util.io.pem.PemReader;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.EndEntityCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.PrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.PublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.TrustAnchorCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.asymmetric.key.pair.grouping._private.key.type.CleartextPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.LocalOrKeystoreAsymmetricKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.LocalOrKeystoreEndEntityCertWithKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev221212.tls.server.grouping.server.identity.auth.type.raw._private.key.RawPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.local.or.truststore.certs.grouping.LocalOrTruststore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.local.or.truststore.certs.grouping.local.or.truststore.local.local.definition.Certificate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.local.or.truststore.certs.grouping.local.or.truststore.local.local.definition.CertificateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.local.or.truststore.certs.grouping.local.or.truststore.local.local.definition.CertificateKey;

public final class TestUtils {

    public static final X509CertInputs X509_RSA_INPUTS = buildX509CertInputs("/x509-cert-rsa");
    public static final X509CertInputs X509_EC_INPUTS = buildX509CertInputs("/x509-cert-ec");

    private TestUtils() {
        // utility class
    }

    public static LocalOrTruststore buildLocalOrTruststore(Map<String, byte[]> certNameToBytesMap) {
        final Map<CertificateKey, Certificate> certMap = certNameToBytesMap.entrySet().stream().collect(
                toMap(
                        entry -> new CertificateKey(entry.getKey()),
                        entry -> new CertificateBuilder().setName(entry.getKey())
                                .setCertData(new TrustAnchorCertCms(entry.getValue())).build()));
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

    private static X509CertInputs buildX509CertInputs(final String resourcePrefix) {
        try {
            // x.509 certificate and public key
            final byte[] certInBytes = resourceToBytes(resourcePrefix + ".crt");
            final X509Certificate x509cert;
            try (InputStream in = new ByteArrayInputStream(certInBytes)) {
                x509cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
            }
            final byte[] publicKey = x509cert.getPublicKey().getEncoded();
            // private key
            final byte[] privateKey;
            try (InputStream in = new ByteArrayInputStream(resourceToBytes(resourcePrefix + ".key"));
                 PemReader pemReader = new PemReader(new InputStreamReader(in))) {
                privateKey = pemReader.readPemObject().getContent();
            }
            // public key from ssh encoded
            final String sshKeyStr = new String(resourceToBytes(resourcePrefix + ".pub")).trim();
            final byte[] sshPublicKey = Base64.getDecoder().decode(sshKeyStr.substring(sshKeyStr.lastIndexOf(' ') + 1));
            return new X509CertInputs(x509cert.getEncoded(), publicKey, privateKey, sshPublicKey);

        } catch (IOException | CertificateException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] resourceToBytes(final String resource) throws IOException {
        try (var in = TestUtils.class.getResourceAsStream(resource)) {
            return Objects.requireNonNull(in).readAllBytes();
        }
    }

    public record X509CertInputs(byte[] certBytes, byte[] publicKey, byte[] privateKey, byte[] sshPublicKey) {
    }
}
