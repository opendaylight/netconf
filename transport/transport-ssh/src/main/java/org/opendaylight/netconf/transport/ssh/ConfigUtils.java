/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionHeartbeatController;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.AsymmetricKeyPairGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SshPublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010._private.key.grouping._private.key.type.CleartextPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.InlineOrKeystoreEndEntityCertWithKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.server.authentication.SshHostKeys;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.TransportParamsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.InlineOrTruststoreCertsGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint8;

final class ConfigUtils {
    private static final int KEEP_ALIVE_DEFAULT_MAX_WAIT_SECONDS = 30;
    private static final int KEEP_ALIVE_DEFAULT_ATTEMPTS = 3;

    private ConfigUtils() {
        // utility class
    }

    static void setTransportParams(final @NonNull BaseBuilder<?, ?> builder,
            final @Nullable TransportParamsGrouping params, final @NonNull KeyExchangePolicy kexExchangePolicy)
            throws UnsupportedConfigurationException {
        builder
            .cipherFactories(EncryptionAlgorithms.factoriesFor(params == null ? null : params.getEncryption()))
            .signatureFactories(PublicKeyAlgorithms.factoriesFor(params == null ? null : params.getHostKey()))
            .keyExchangeFactories(kexExchangePolicy.exchangeConfigOrDefault(params == null ? null
                : params.getKeyExchange()))
            .macFactories(MacAlgorithms.factoriesFor(params == null ? null : params.getMac()));
    }

    @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "maxAttempts usage need clarification")
    static void setKeepAlives(final @NonNull FactoryManager factoryMgr, final @Nullable Uint16 cfgMaxWait,
            final @Nullable Uint8 cfgMaxAttempts) {
        // FIXME: utilize max attempts
        final var maxAttempts = cfgMaxAttempts == null ? KEEP_ALIVE_DEFAULT_ATTEMPTS : cfgMaxAttempts.intValue();
        final var maxWait = cfgMaxWait == null ? KEEP_ALIVE_DEFAULT_MAX_WAIT_SECONDS : cfgMaxWait.intValue();
        factoryMgr.setSessionHeartbeat(SessionHeartbeatController.HeartbeatType.IGNORE, Duration.ofSeconds(maxWait));
    }

    static List<KeyPair> extractServerHostKeys(
            final List<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010
                    .ssh.server.grouping.server.identity.HostKey> serverHostKeys)
            throws UnsupportedConfigurationException {
        var listBuilder = ImmutableList.<KeyPair>builder();
        for (var hostKey : serverHostKeys) {
            if (hostKey.getHostKeyType()
                    instanceof org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010
                    .ssh.server.grouping.server.identity.host.key.host.key.type.PublicKey publicKey
                    && publicKey.getPublicKey() != null) {
                listBuilder.add(extractKeyPair(publicKey.getPublicKey().getInlineOrKeystore()));
            } else if (hostKey.getHostKeyType()
                    instanceof org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010
                    .ssh.server.grouping.server.identity.host.key.host.key.type.Certificate certificate
                    && certificate.getCertificate() != null) {
                listBuilder.add(extractCertificateEntry(certificate.getCertificate()).getKey());
            }
        }
        return listBuilder.build();
    }

    static KeyPair extractKeyPair(
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010
                    .inline.or.keystore.asymmetric.key.grouping.InlineOrKeystore input)
            throws UnsupportedConfigurationException {
        final var inline = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010
                .inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.Inline.class, input);
        final var inlineDef = inline.getInlineDefinition();
        if (inlineDef == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inline);
        }
        return extractKeyPair(inlineDef);
    }

    private static KeyPair extractKeyPair(final AsymmetricKeyPairGrouping input)
            throws UnsupportedConfigurationException {
        final var keyFormat = input.getPrivateKeyFormat();
        final String privateKeyAlgorithm;
        if (EcPrivateKeyFormat.VALUE.equals(keyFormat)) {
            privateKeyAlgorithm = KeyUtils.EC_ALGORITHM;
        } else if (RsaPrivateKeyFormat.VALUE.equals(input.getPrivateKeyFormat())) {
            privateKeyAlgorithm = KeyUtils.RSA_ALGORITHM;
        } else {
            throw new UnsupportedConfigurationException("Unsupported private key format " + keyFormat);
        }
        final byte[] privateKeyBytes;
        if (!(input.getPrivateKeyType() instanceof CleartextPrivateKey clearText)) {
            throw new UnsupportedConfigurationException("Unsupported private key type " + input.getPrivateKeyType());
        }
        privateKeyBytes = clearText.requireCleartextPrivateKey();

        final var publicKeyFormat = input.getPublicKeyFormat();
        final var publicKeyBytes = input.getPublicKey();
        final boolean isSshPublicKey;
        if (SubjectPublicKeyInfoFormat.VALUE.equals(publicKeyFormat)) {
            isSshPublicKey = false;
        } else if (SshPublicKeyFormat.VALUE.equals(publicKeyFormat)) {
            isSshPublicKey = true;
        } else {
            throw new UnsupportedConfigurationException("Unsupported public key format " + publicKeyFormat);
        }

        final var privateKey = KeyUtils.buildPrivateKey(privateKeyAlgorithm, privateKeyBytes);
        final var publicKey = isSshPublicKey ? KeyUtils.buildPublicKeyFromSshEncoding(publicKeyBytes)
                : KeyUtils.buildX509PublicKey(privateKeyAlgorithm, publicKeyBytes);
        /*
            ietf-crypto-types:grouping asymmetric-key-pair-grouping
            "A private key and its associated public key.  Implementations
            SHOULD ensure that the two keys are a matching pair."
         */
        KeyUtils.validateKeyPair(publicKey, privateKey);
        return new KeyPair(publicKey, privateKey);
    }

    static List<Certificate> extractCertificates(final @Nullable InlineOrTruststoreCertsGrouping input)
            throws UnsupportedConfigurationException {
        if (input == null) {
            return List.of();
        }
        final var inline = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore
                        .rev241010.inline.or.truststore.certs.grouping.inline.or.truststore.Inline.class,
                input.getInlineOrTruststore());
        final var inlineDef = inline.getInlineDefinition();
        if (inlineDef == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inline);
        }
        final var listBuilder = ImmutableList.<Certificate>builder();
        for (var cert : inlineDef.nonnullCertificate().values()) {
            listBuilder.add(KeyUtils.buildX509Certificate(cert.requireCertData().getValue()));
        }
        return listBuilder.build();
    }

    private static Map.Entry<KeyPair, List<X509Certificate>> extractCertificateEntry(
            final InlineOrKeystoreEndEntityCertWithKeyGrouping input) throws UnsupportedConfigurationException {
        final var inline = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010
                        .inline.or.keystore.end.entity.cert.with.key.grouping.inline.or.keystore.Inline.class,
                input.getInlineOrKeystore());
        final var inlineDef = inline.getInlineDefinition();
        if (inlineDef == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inline);
        }
        final var keyPair = extractKeyPair(inlineDef);
        final var certificate = KeyUtils.buildX509Certificate(inlineDef.requireCertData().getValue());
        /*
          ietf-crypto-types:asymmetric-key-pair-with-cert-grouping
          "A private/public key pair and an associated certificate.
          Implementations SHOULD assert that certificates contain the matching public key."
         */
        KeyUtils.validatePublicKey(keyPair.getPublic(), certificate);
        return new SimpleImmutableEntry<>(keyPair, List.of(certificate));
    }

    private static <T> T ofType(final Class<T> expectedType, final Object obj)
            throws UnsupportedConfigurationException {
        if (!expectedType.isInstance(obj)) {
            throw new UnsupportedConfigurationException("Expected type: " + expectedType
                    + " actual: " + obj.getClass());
        }
        return expectedType.cast(obj);
    }

    static List<PublicKey> extractPublicKeys(
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010
                    .inline.or.truststore._public.keys.grouping.InlineOrTruststore input)
            throws UnsupportedConfigurationException {
        final var inline = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010
                .inline.or.truststore._public.keys.grouping.inline.or.truststore.Inline.class, input);
        final var inlineDef = inline.getInlineDefinition();
        if (inlineDef == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inline);
        }

        final var publicKey = inlineDef.getPublicKey();
        if (publicKey == null) {
            return List.of();
        }

        final var listBuilder = ImmutableList.<PublicKey>builder();
        for (var entry : publicKey.entrySet()) {
            if (!SshPublicKeyFormat.VALUE.equals(entry.getValue().getPublicKeyFormat())) {
                throw new UnsupportedConfigurationException("ssh public key format is expected");
            }
            listBuilder.add(KeyUtils.buildPublicKeyFromSshEncoding(entry.getValue().getPublicKey()));
        }
        return listBuilder.build();
    }

    static List<PublicKey> extractPublicKeys(final @Nullable SshHostKeys sshHostKeys)
            throws UnsupportedConfigurationException {
        return sshHostKeys == null ? List.of() : extractPublicKeys(sshHostKeys.getInlineOrTruststore());
    }

    static <K, V> @NonNull List<V> mapValues(final Map<K, ? extends V> map, final List<K> values,
            final String errorTemplate) throws UnsupportedConfigurationException {
        final var builder = ImmutableList.<V>builderWithExpectedSize(values.size());
        for (var value : values) {
            final var mapped = map.get(value);
            if (mapped == null) {
                throw new UnsupportedOperationException(String.format(errorTemplate, value));
            }
            builder.add(mapped);
        }
        return builder.build();
    }
}
