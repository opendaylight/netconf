/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.crypt.hash.rev140806.CryptHash;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.EndEntityCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.PrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.SshPublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.TrustAnchorCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.asymmetric.key.pair.grouping._private.key.type.CleartextPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.ClientIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.ServerAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.ServerAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.server.authentication.CaCertsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.server.authentication.SshHostKeysBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.ClientAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.ClientAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.ServerIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.ServerIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.client.authentication.UsersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.client.authentication.users.User;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.client.authentication.users.UserBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.client.authentication.users.user.PublicKeysBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417.inline.or.truststore.certs.grouping.inline.or.truststore.inline.inline.definition.CertificateBuilder;
import org.opendaylight.yangtools.yang.binding.util.BindingMap;

public final class TestUtils {

    private static final String PUBLIC_KEY_NAME = "public-key-name";
    private static final String HOST_KEY_NAME = "host-key-name";
    private static final String CERTIFICATE_NAME = "certificate-name";
    private static final String CERTIFICATE_CN = "certificate-cn";
    private static final String BC = BouncyCastleProvider.PROVIDER_NAME;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private TestUtils() {
        // utility class
    }

    public static ServerIdentity buildServerIdentityWithKeyPair(final KeyData keyData) {
        return new ServerIdentityBuilder().setHostKey(List.of(buildServerHostKeyWithKeyPair(keyData))).build();
    }

    public static ServerIdentity buildServerIdentityWithCertificate(final KeyData keyData) {
        return new ServerIdentityBuilder().setHostKey(List.of(buildServerHostKeyWithCertificate(keyData))).build();
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417
            .ssh.server.grouping.server.identity.HostKey buildServerHostKeyWithKeyPair(final KeyData keyData) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417
            .ssh.server.grouping.server.identity.HostKeyBuilder()
            .setName(HOST_KEY_NAME)
            .setHostKeyType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417
                .ssh.server.grouping.server.identity.host.key.host.key.type.PublicKeyBuilder()
                .setPublicKey(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417
                    .ssh.server.grouping.server.identity.host.key.host.key.type._public.key.PublicKeyBuilder()
                    .setInlineOrKeystore(buildAsymmetricKeyLocal(keyData))
                    .build())
                .build())
            .build();
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417
            .ssh.server.grouping.server.identity.HostKey buildServerHostKeyWithCertificate(final KeyData keyData) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417
            .ssh.server.grouping.server.identity.HostKeyBuilder()
            .setName(HOST_KEY_NAME)
            .setHostKeyType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417
                .ssh.server.grouping.server.identity.host.key.host.key.type.CertificateBuilder()
                .setCertificate(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417
                    .ssh.server.grouping.server.identity.host.key.host.key.type.certificate.CertificateBuilder()
                    .setInlineOrKeystore(buildEndEntityCertWithKeyLocal(keyData))
                    .build())
                .build())
            .build();
    }

    public static ServerAuthentication buildServerAuthWithPublicKey(final KeyData keyData) {
        return new ServerAuthenticationBuilder()
            .setSshHostKeys(new SshHostKeysBuilder()
                .setInlineOrTruststore(buildTruststorePublicKeyLocal(keyData))
                .build())
            .build();
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417
            .inline.or.truststore._public.keys.grouping.inline.or.truststore.Inline buildTruststorePublicKeyLocal(
            final KeyData keyData) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417
            .inline.or.truststore._public.keys.grouping.inline.or.truststore.InlineBuilder()
            .setInlineDefinition(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417
                .inline.or.truststore._public.keys.grouping.inline.or.truststore.inline.InlineDefinitionBuilder()
                .setPublicKey(BindingMap.of(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore
                    .rev230417.inline.or.truststore._public.keys.grouping.inline.or.truststore.inline.inline.definition
                    .PublicKeyBuilder()
                        .setName(PUBLIC_KEY_NAME)
                        .setPublicKeyFormat(SshPublicKeyFormat.VALUE)
                        .setPublicKey(keyData.publicKeySshBytes())
                        .build()))
                .build())
            .build();
    }

    public static ServerAuthentication buildServerAuthWithCertificate(final KeyData keyData) {
        // NB both CA anc EE certificates are processed same way, no reason for additional eeCerts builder
        return new ServerAuthenticationBuilder()
            .setCaCerts(new CaCertsBuilder()
                .setInlineOrTruststore(buildTruststoreCertificatesLocal(keyData.certificateBytes()))
                .build())
            .build();
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417
            .inline.or.truststore.certs.grouping.inline.or.truststore.Inline buildTruststoreCertificatesLocal(
            final byte[] certificateBytes) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417
            .inline.or.truststore.certs.grouping.inline.or.truststore.InlineBuilder()
            .setInlineDefinition(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417
                .inline.or.truststore.certs.grouping.inline.or.truststore.inline.InlineDefinitionBuilder()
                .setCertificate(BindingMap.of(new CertificateBuilder()
                    .setName(CERTIFICATE_NAME)
                    .setCertData(new TrustAnchorCertCms(certificateBytes))
                    .build()))
                .build())
            .build();
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417
            .inline.or.keystore.asymmetric.key.grouping.InlineOrKeystore buildAsymmetricKeyLocal(final KeyData data) {
        return buildAsymmetricKeyLocal(data.algorithm(), data.publicKeyBytes(), data.privateKeyBytes());
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417
            .inline.or.keystore.asymmetric.key.grouping.InlineOrKeystore buildAsymmetricKeyLocal(final String algorithm,
                final byte[] publicKeyBytes, final byte[] privateKeyBytes) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417
            .inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.InlineBuilder()
            .setInlineDefinition(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417
                .inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.inline.InlineDefinitionBuilder()
                .setPublicKeyFormat(SubjectPublicKeyInfoFormat.VALUE)
                .setPublicKey(publicKeyBytes)
                .setPrivateKeyFormat(getPrivateKeyFormat(algorithm))
                .setPrivateKeyType(new CleartextPrivateKeyBuilder().setCleartextPrivateKey(privateKeyBytes).build())
                .build())
            .build();
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417
            .inline.or.keystore.end.entity.cert.with.key.grouping.InlineOrKeystore buildEndEntityCertWithKeyLocal(
            final KeyData keyData) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417
            .inline.or.keystore.end.entity.cert.with.key.grouping.inline.or.keystore.InlineBuilder()
            .setInlineDefinition(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417
                .inline.or.keystore.end.entity.cert.with.key.grouping.inline.or.keystore.inline
                .InlineDefinitionBuilder()
                .setPublicKeyFormat(SubjectPublicKeyInfoFormat.VALUE)
                .setPublicKey(keyData.publicKeyBytes())
                .setPrivateKeyFormat(getPrivateKeyFormat(keyData.algorithm()))
                .setPrivateKeyType(new CleartextPrivateKeyBuilder()
                    .setCleartextPrivateKey(keyData.privateKeyBytes()).build())
                .setCertData(new EndEntityCertCms(keyData.certificateBytes()))
                .build())
            .build();
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
        return new ClientAuthenticationBuilder()
            .setUsers(new UsersBuilder().setUser(BindingMap.of(user)).build())
            .build();
    }

    private static User buildServerUserHostBased(final String userName, final byte[] publicKeyBytes) {
        return new UserBuilder()
            .setName(userName)
            .setHostbased(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417
                .ssh.server.grouping.client.authentication.users.user.HostbasedBuilder()
                .setInlineOrTruststore(buildPublicKeyLocal(publicKeyBytes))
                .build())
            .build();
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417
            .inline.or.truststore._public.keys.grouping.inline.or.truststore.Inline buildPublicKeyLocal(
            final byte[] publicKeyBytes) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417
            .inline.or.truststore._public.keys.grouping.inline.or.truststore.InlineBuilder()
            .setInlineDefinition(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417
                .inline.or.truststore._public.keys.grouping.inline.or.truststore.inline.InlineDefinitionBuilder()
                .setPublicKey(BindingMap.of(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
                    .truststore.rev230417.inline.or.truststore._public.keys.grouping.inline.or.truststore.inline.inline
                    .definition.PublicKeyBuilder()
                    .setPublicKeyFormat(SshPublicKeyFormat.VALUE)
                    .setName(PUBLIC_KEY_NAME)
                    .setPublicKey(publicKeyBytes)
                    .build()))
                .build())
            .build();
    }

    public static User buildServerUserWithPublicKey(final String userName, final byte[] publicKeyBytes) {
        return new UserBuilder()
            .setName(userName)
            .setPublicKeys(new PublicKeysBuilder().setInlineOrTruststore(buildPublicKeyLocal(publicKeyBytes)).build())
            .build();
    }

    private static User buildServerUserWithPassword(final String userName, final String cryptHash) {
        return new UserBuilder().setName(userName).setPassword(new CryptHash(cryptHash)).build();
    }

    public static ClientIdentity buildClientIdentityWithPassword(final String username, final String password) {
        return new ClientIdentityBuilder()
            .setUsername(username)
            .setPassword(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417
                .ssh.client.grouping.client.identity.PasswordBuilder()
                .setPasswordType(new CleartextPasswordBuilder().setCleartextPassword(password).build()).build())
            .build();
    }

    public static ClientIdentity buildClientIdentityHostBased(final String username, final KeyData data) {
        return new ClientIdentityBuilder()
            .setUsername(username)
            .setHostbased(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417
                .ssh.client.grouping.client.identity.HostbasedBuilder()
                .setInlineOrKeystore(buildAsymmetricKeyLocal(data))
                .build())
            .build();
    }

    public static ClientIdentity buildClientIdentityWithPublicKey(final String username, final KeyData data) {
        return new ClientIdentityBuilder()
            .setUsername(username)
            .setPublicKey(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417
                .ssh.client.grouping.client.identity.PublicKeyBuilder()
                .setInlineOrKeystore(buildAsymmetricKeyLocal(data))
                .build())
            .build();
    }

    private static PrivateKeyFormat getPrivateKeyFormat(final String algorithm) {
        return isRSA(algorithm) ? RsaPrivateKeyFormat.VALUE : EcPrivateKeyFormat.VALUE;
    }

    public static KeyData generateKeyPairWithCertificate(final String algorithm) throws IOException {
        try {
            final var keyPairGenerator = KeyPairGenerator.getInstance(algorithm, BC);
            if (isRSA(algorithm)) {
                keyPairGenerator.initialize(
                        new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4), SECURE_RANDOM);
            } else {
                keyPairGenerator.initialize(new ECGenParameterSpec("prime256v1"), SECURE_RANDOM);
            }
            final var keyPair = keyPairGenerator.generateKeyPair();
            final var certificate = generateCertificate(keyPair,
                    isRSA(algorithm) ? "SHA256withRSA" : "SHA256withECDSA");
            final var publicKeyBytes = keyPair.getPublic().getEncoded();
            final var privateKeyBytes = keyPair.getPrivate().getEncoded();

            return new KeyData(algorithm, privateKeyBytes, publicKeyBytes,
                    OpenSSHPublicKeyUtil.encodePublicKey(PublicKeyFactory.createKey(publicKeyBytes)),
                    certificate.getEncoded());

        } catch (NoSuchAlgorithmException | NoSuchProviderException | OperatorCreationException | CertificateException
                 | InvalidAlgorithmParameterException e) {
            throw new IOException(e); // simplify method signature
        }
    }

    private static X509Certificate generateCertificate(final KeyPair keyPair, final String hashAlgorithm)
            throws OperatorCreationException, CertificateException {
        final var now = Instant.now();
        final var contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate());

        final var x500Name = new X500Name("CN=" + CERTIFICATE_CN);
        final var certificateBuilder = new JcaX509v3CertificateBuilder(x500Name,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now), Date.from(now.plus(Duration.ofDays(365))),
                x500Name,
                keyPair.getPublic());
        return new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
    }

    private static boolean isRSA(final String algorithm) {
        return "RSA".equals(algorithm);
    }

    public record KeyData(String algorithm, byte[] privateKeyBytes, byte[] publicKeyBytes,
                          byte[] publicKeySshBytes, byte[] certificateBytes) {
    }
}
