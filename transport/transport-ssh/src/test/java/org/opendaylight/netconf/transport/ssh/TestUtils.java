/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.crypt.hash.rev140806.CryptHash;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.EndEntityCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.SshPublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.TrustAnchorCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.asymmetric.key.pair.grouping._private.key.type.CleartextPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev221212.ssh.client.grouping.ClientIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev221212.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev221212.ssh.client.grouping.server.authentication.SshHostKeys;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.ClientAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.ClientAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.client.authentication.UsersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.client.authentication.users.User;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.client.authentication.users.UserBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.client.authentication.users.user.PublicKeysBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.local.or.truststore.certs.grouping.LocalOrTruststore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.local.or.truststore.certs.grouping.local.or.truststore.local.local.definition.Certificate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.local.or.truststore.certs.grouping.local.or.truststore.local.local.definition.CertificateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.local.or.truststore.certs.grouping.local.or.truststore.local.local.definition.CertificateKey;

public final class TestUtils {

    private static final RSAKeyPairGenerator RSA_KEY_PAIR_GENERATOR = new RSAKeyPairGenerator();
    private static final ECKeyPairGenerator EC_KEY_PAIR_GENERATOR = new ECKeyPairGenerator();
    private static final String PUBLIC_KEY_NAME = "public-key-name";

    static {
        final var random = new SecureRandom();
        RSA_KEY_PAIR_GENERATOR.init(new RSAKeyGenerationParameters(BigInteger.valueOf(123L), random, 1024, 100));
        EC_KEY_PAIR_GENERATOR.init(
                new ECKeyGenerationParameters(new ECDomainParameters(ECNamedCurveTable.getByName("P-256")), random));
    }

    private TestUtils() {
        // utility class
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212
            .ssh.server.grouping.server.identity.HostKey buildServerHostKey(final String name, final KeyPair keyPair) {
        var local = buildAsymmetricKeyLocal(keyPair);
        var publicKey = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212
                .ssh.server.grouping.server.identity.host.key.host.key.type.PublicKeyBuilder()
                .setPublicKey(
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212
                                .ssh.server.grouping.server.identity.host.key.host.key.type._public.key
                                .PublicKeyBuilder().setLocalOrKeystore(local).build()
                ).build();
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212
                .ssh.server.grouping.server.identity.HostKeyBuilder().setName(name).setHostKeyType(publicKey).build();
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212
            .ssh.server.grouping.server.identity.HostKey buildServerHostKey(final String name, final KeyPair keyPair,
            final X509Certificate certificate) throws CertificateEncodingException {
        var local = buildEndEntityCertWithKeyLocal(keyPair, certificate);
        var cert = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212
                .ssh.server.grouping.server.identity.host.key.host.key.type.CertificateBuilder()
                .setCertificate(
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212
                                .ssh.server.grouping.server.identity.host.key.host.key.type.certificate
                                .CertificateBuilder().setLocalOrKeystore(local).build()
                ).build();
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212
                .ssh.server.grouping.server.identity.HostKeyBuilder().setName(name).setHostKeyType(cert).build();
    }

    public static SshHostKeys buildServerAuthHostKeys() {
        return null;
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
            .local.or.keystore.asymmetric.key.grouping.LocalOrKeystore buildAsymmetricKeyLocal(
            final KeyPair keyPair) {
        return buildAsymmetricKeyLocal(keyPair.getPrivate().getAlgorithm(),
                keyPair.getPublic().getEncoded(), keyPair.getPrivate().getEncoded());
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
            .local.or.keystore.asymmetric.key.grouping.LocalOrKeystore buildAsymmetricKeyLocal(
            final KeyData data) {
        return buildAsymmetricKeyLocal(data.algorithm(), data.publicKeyBytes(), data.privateKeyBytes());
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
            .local.or.keystore.asymmetric.key.grouping.LocalOrKeystore buildAsymmetricKeyLocal(
            final String algorithm, final byte[] publicKeyBytes, final byte[] privateKeyBytes) {
        var keyFormat = "RSA".equals(algorithm)
                ? RsaPrivateKeyFormat.VALUE : EcPrivateKeyFormat.VALUE;
        final var localDef = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
                .local.or.keystore.asymmetric.key.grouping.local.or.keystore.local.LocalDefinitionBuilder()
                .setPublicKeyFormat(SubjectPublicKeyInfoFormat.VALUE)
                .setPublicKey(publicKeyBytes)
                .setPrivateKeyFormat(keyFormat)
                .setPrivateKeyType(new CleartextPrivateKeyBuilder().setCleartextPrivateKey(privateKeyBytes).build())
                .build();
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
                .local.or.keystore.asymmetric.key.grouping.local.or.keystore.LocalBuilder()
                .setLocalDefinition(localDef).build();
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
            .local.or.keystore.end.entity.cert.with.key.grouping.LocalOrKeystore buildEndEntityCertWithKeyLocal(
            final KeyPair keyPair, final X509Certificate certificate) throws CertificateEncodingException {
        var keyFormat = "RSA".equals(keyPair.getPrivate().getAlgorithm())
                ? RsaPrivateKeyFormat.VALUE : EcPrivateKeyFormat.VALUE;
        final var localDef = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
                .local.or.keystore.end.entity.cert.with.key.grouping.local.or.keystore.local.LocalDefinitionBuilder()
                .setPublicKeyFormat(SubjectPublicKeyInfoFormat.VALUE)
                .setPublicKey(keyPair.getPublic().getEncoded())
                .setPrivateKeyFormat(keyFormat)
                .setPrivateKeyType(new CleartextPrivateKeyBuilder()
                        .setCleartextPrivateKey(keyPair.getPrivate().getEncoded()).build())
                .setCertData(new EndEntityCertCms(certificate.getEncoded()))
                .build();
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212
                .local.or.keystore.end.entity.cert.with.key.grouping.local.or.keystore.LocalBuilder()
                .setLocalDefinition(localDef).build();
    }

    public static LocalOrTruststore buildLocalOrTruststore(final Map<String, byte[]> certNameToBytesMap) {
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

    public static ClientAuthentication buildClientAuthWithPassword(final String userName, final String cryptHash) {
        return buildClientAuth(buildServerUserWithPassword(userName, cryptHash));
    }

    public static ClientAuthentication buildClientAuthHostBased(final String userName, final KeyData keyData) {
        return buildClientAuth(buildServerUserHostBased(userName, keyData.publicKeySshBytes()));
    }

    public static ClientAuthentication buildClientAuthWithPublicKey(final String userName, final KeyData keyData) {
        return buildClientAuth(buildServerUserWithPublicKey(userName, keyData.publicKeySshBytes()));
    }

    private static ClientAuthentication buildClientAuth(final User user) {
        return new ClientAuthenticationBuilder().setUsers(
                new UsersBuilder().setUser(Map.of(user.key(), user)).build()).build();
    }

    public static User buildServerUserHostBased(final String userName, final byte[] publicKeyBytes) {
        final var hostbased = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212
                .ssh.server.grouping.client.authentication.users.user.HostbasedBuilder()
                .setLocalOrTruststore(buildPublicKeyLocal(PUBLIC_KEY_NAME, publicKeyBytes)).build();
        return new UserBuilder().setName(userName).setHostbased(hostbased).build();
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212
            .local.or.truststore._public.keys.grouping.local.or.truststore.Local buildPublicKeyLocal(
            final String keyName, final byte[] publicKeyBytes) {
        final var publicKey = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212
                .local.or.truststore._public.keys.grouping.local.or.truststore.local.local.definition.PublicKeyBuilder()
                .setPublicKeyFormat(SshPublicKeyFormat.VALUE)
                .setName(keyName)
                .setPublicKey(publicKeyBytes).build();
        final var localDef = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212
                .local.or.truststore._public.keys.grouping.local.or.truststore.local.LocalDefinitionBuilder()
                .setPublicKey(Map.of(publicKey.key(), publicKey)).build();
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212
                .local.or.truststore._public.keys.grouping.local.or.truststore.LocalBuilder()
                .setLocalDefinition(localDef).build();
    }

    public static User buildServerUserWithPublicKey(final String userName, final byte[] publicKeyBytes) {
        final var publicKeys = new PublicKeysBuilder()
                .setLocalOrTruststore(buildPublicKeyLocal(PUBLIC_KEY_NAME, publicKeyBytes)).build();
        return new UserBuilder().setName(userName).setPublicKeys(publicKeys).build();
    }

    private static User buildServerUserWithPassword(final String userName, final String cryptHash) {
        return new UserBuilder().setName(userName).setPassword(new CryptHash(cryptHash)).build();
    }

    public static ClientIdentity buildClientIdWithPassword(final String username, final String password) {
        return new ClientIdentityBuilder().setUsername(username).setPassword(
                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev221212
                        .ssh.client.grouping.client.identity.PasswordBuilder()
                        .setPasswordType(
                                new CleartextPasswordBuilder().setCleartextPassword(password).build()
                        ).build()).build();
    }

    public static ClientIdentity buildClientIdHostBased(final String username, final KeyData data) {
        return new ClientIdentityBuilder().setUsername(username).setHostbased(
                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev221212
                        .ssh.client.grouping.client.identity.HostbasedBuilder()
                        .setLocalOrKeystore(buildAsymmetricKeyLocal(data)).build()
        ).build();
    }

    public static ClientIdentity buildClientIdWithPublicKey(final String username, final KeyData data) {
        return new ClientIdentityBuilder().setUsername(username).setPublicKey(
                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev221212
                        .ssh.client.grouping.client.identity.PublicKeyBuilder()
                        .setLocalOrKeystore(buildAsymmetricKeyLocal(data)).build()
        ).build();
    }

    public static KeyPair generateKeyPair(final String algorithm) throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance(algorithm).generateKeyPair();
    }

    public static X509Certificate generateCertificateEntry(final KeyPair keyPair, final String hashAlgorithm,
            final String cn) throws OperatorCreationException, CertificateException {
        final var now = Instant.now();
        final var contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate());
        final var x500Name = new X500Name("CN=" + cn);
        final var certificateBuilder = new JcaX509v3CertificateBuilder(x500Name,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now),
                Date.from(now.plus(Duration.ofDays(365))),
                x500Name,
                keyPair.getPublic());
        return new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
    }

    public static KeyData generateKeyPairWithSsh(final String algorithm) throws IOException {
        final var bcKeyPair = "RSA".equals(algorithm)
                ? RSA_KEY_PAIR_GENERATOR.generateKeyPair()
                : EC_KEY_PAIR_GENERATOR.generateKeyPair();
        return new KeyData(algorithm,
                PrivateKeyInfoFactory.createPrivateKeyInfo(bcKeyPair.getPrivate()).getEncoded(),
                SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(bcKeyPair.getPublic()).getEncoded(),
                OpenSSHPublicKeyUtil.encodePublicKey(bcKeyPair.getPublic()));
    }

    public record KeyData(String algorithm, byte[] privateKeyBytes, byte[] publicKeyBytes,
                          byte[] publicKeySshBytes) {
    }

}
