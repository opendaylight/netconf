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
import java.time.Duration;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionHeartbeatController;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.crypto.CMSCertificateParser;
import org.opendaylight.netconf.transport.crypto.KeyPairParser;
import org.opendaylight.netconf.transport.crypto.PublicKeyParser;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.InlineOrKeystoreEndEntityCertWithKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.InlineOrKeystore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.InlineOrTruststoreCertsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.inline.or.truststore._public.keys.grouping.InlineOrTruststore;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint8;

final class ConfigUtils {
    private static final int KEEP_ALIVE_DEFAULT_MAX_WAIT_SECONDS = 30;
    private static final int KEEP_ALIVE_DEFAULT_ATTEMPTS = 3;

    private ConfigUtils() {
        // utility class
    }

    @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "maxAttempts usage need clarification")
    static void setKeepAlives(final @NonNull FactoryManager factoryMgr, final @Nullable Uint16 cfgMaxWait,
            final @Nullable Uint8 cfgMaxAttempts) {
        // FIXME: utilize max attempts
        final var maxAttempts = cfgMaxAttempts == null ? KEEP_ALIVE_DEFAULT_ATTEMPTS : cfgMaxAttempts.intValue();
        final var maxWait = cfgMaxWait == null ? KEEP_ALIVE_DEFAULT_MAX_WAIT_SECONDS : cfgMaxWait.intValue();
        factoryMgr.setSessionHeartbeat(SessionHeartbeatController.HeartbeatType.IGNORE, Duration.ofSeconds(maxWait));
    }

    static KeyPair extractKeyPair(final InlineOrKeystore input) throws UnsupportedConfigurationException {
        final var inline = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010
                .inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.Inline.class, input);
        final var inlineDef = inline.getInlineDefinition();
        if (inlineDef == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inline);
        }
        return KeyPairParser.parseKeyPair(inlineDef);
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
            listBuilder.add(CMSCertificateParser.parseCertificate(cert.requireCertData()));
        }
        return listBuilder.build();
    }

    static Map.Entry<KeyPair, List<Certificate>> extractCertificateEntry(
            final InlineOrKeystoreEndEntityCertWithKeyGrouping input) throws UnsupportedConfigurationException {
        final var inline = ofType(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010
                        .inline.or.keystore.end.entity.cert.with.key.grouping.inline.or.keystore.Inline.class,
                input.getInlineOrKeystore());
        final var inlineDef = inline.getInlineDefinition();
        if (inlineDef == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inline);
        }
        final var keyPair = KeyPairParser.parseKeyPair(inlineDef);
        final var certificate = CMSCertificateParser.parseCertificate(inlineDef.requireCertData());
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

    static List<PublicKey> extractPublicKeys(final InlineOrTruststore input) throws UnsupportedConfigurationException {
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
            listBuilder.add(PublicKeyParser.parsePublicKey(entry.getValue()));
        }
        return listBuilder.build();
    }
}
