/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.shaded.sshd.common.cipher.BuiltinCiphers;
import org.opendaylight.netconf.shaded.sshd.common.kex.BuiltinDHFactories;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchangeFactory;
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.signature.BuiltinSignatures;

/**
 * This is a test for the four aspects of SSH crypto provided out of the box by SSHD
 * <ul>
 *   <li>{@link BuiltinCiphers}</li>
 *   <li>{@link BuiltinDHFactories}</li>
 *   <li>{@link BuiltinMacs}</li>
 *   <li>{@link BuiltinSignatures}</li>
 * </ul>
 * and our coverage in {@link TransportUtils}' lookup maps. This ensures that we are cognizant of new algorithms being
 * available, so that we can updated our mapping accordingly.
 *
 * <p>Note that there are two-way assertions to suppressions, so that we are forced to keep them consistent.
 */
class BuiltinCoverageTest {
    @Test
    void coveredBuiltinCiphers() {
        final var suppressed = new HashSet<BuiltinCiphers>();
        final var unmatched = new ArrayList<BuiltinCiphers>();

        for (var alg : BuiltinCiphers.values()) {
            if (SUPPRESSED_CIPHERS.contains(alg)) {
                assertTrue(suppressed.add(alg));
            } else if (!TransportUtils.CIPHERS.containsValue(alg)) {
                unmatched.add(alg);
            }
        }

        assertEquals(List.of(), unmatched);
        assertEquals(suppressed, SUPPRESSED_CIPHERS);
    }

    private static final Set<BuiltinCiphers> SUPPRESSED_CIPHERS = Set.of(
        // FIXME: these seem to indicate a gap in our coverage
        BuiltinCiphers.aes256gcm,
        BuiltinCiphers.cc20p1305_openssh);

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource
    void coveredBuiltinDHFactories(final Map<?, KeyExchangeFactory> map) {
        final var suppressed = new HashSet<BuiltinDHFactories>();
        final var unmatched = new ArrayList<BuiltinDHFactories>();

        for (var alg : BuiltinDHFactories.values()) {
            if (SUPPRESSED_DHFACTORIES.contains(alg)) {
                assertTrue(suppressed.add(alg));
            } else if (map.values().stream().noneMatch(f -> alg.getName().equals(f.getName()))) {
                unmatched.add(alg);
            }
        }

        assertEquals(List.of(), unmatched);
        assertEquals(suppressed, SUPPRESSED_DHFACTORIES);
    }

    private static List<Arguments> coveredBuiltinDHFactories() {
        return List.of(
            arguments(named("client KEXs", TransportUtils.CLIENT_KEXS)),
            arguments(named("server KEXs", TransportUtils.SERVER_KEXS)));
    }

    private static final Set<BuiltinDHFactories> SUPPRESSED_DHFACTORIES = Set.of(
        // FIXME: explain these omissions and consider providing them, if possible
        // https://www.libssh.org/2013/11/03/openssh-introduces-curve25519-sha256libssh-org-key-exchange/
        BuiltinDHFactories.curve25519_libssh,
        BuiltinDHFactories.mlkem768x25519,
        BuiltinDHFactories.mlkem768nistp256,
        BuiltinDHFactories.mlkem1024nistp384,
        BuiltinDHFactories.sntrup761x25519,
        BuiltinDHFactories.sntrup761x25519_openssh);

    @Test
    void coveredBuiltinMacs() {
        final var suppressed = new HashSet<BuiltinMacs>();
        final var unmatched = new ArrayList<BuiltinMacs>();

        for (var alg : BuiltinMacs.values()) {
            if (SUPPRESSED_MACS.contains(alg)) {
                assertTrue(suppressed.add(alg));
            } else if (!TransportUtils.MACS.containsValue(alg)) {
                unmatched.add(alg);
            }
        }

        assertEquals(List.of(), unmatched);
        assertEquals(suppressed, SUPPRESSED_MACS);
    }

    private static final Set<BuiltinMacs> SUPPRESSED_MACS = Set.of(
        // These three are encrypt-then-MAC modes, originally introduced in
        // https://cvsweb.openbsd.org/cgi-bin/cvsweb/src/usr.bin/ssh/PROTOCOL?rev=1.18&content-type=text/x-cvsweb-markup
        // and IANA does not cover the assignment
        // FIXME: we should provide these extensions
        BuiltinMacs.hmacsha1etm,
        BuiltinMacs.hmacsha256etm,
        BuiltinMacs.hmacsha512etm);

    @Test
    void coveredBuiltinSignatures() {
        final var suppressed = new HashSet<BuiltinSignatures>();
        final var unmatched = new ArrayList<BuiltinSignatures>();

        for (var alg : BuiltinSignatures.values()) {
            if (SUPPRESSED_SIGNATURES.contains(alg)) {
                assertTrue(suppressed.add(alg));
            } else if (!TransportUtils.SIGNATURES.containsValue(alg)) {
                unmatched.add(alg);
            }
        }

        assertEquals(List.of(), unmatched);
        assertEquals(suppressed, SUPPRESSED_SIGNATURES);
    }

    @SuppressWarnings("deprecation")
    private static final Set<BuiltinSignatures> SUPPRESSED_SIGNATURES = Set.of(
        // FIXME: explain these omissions and consider providing them, if possible
        BuiltinSignatures.dsa_cert,
        BuiltinSignatures.rsa_cert,
        BuiltinSignatures.rsaSHA256_cert,
        BuiltinSignatures.rsaSHA512_cert,
        BuiltinSignatures.nistp256_cert,
        BuiltinSignatures.nistp384_cert,
        BuiltinSignatures.nistp521_cert,
        BuiltinSignatures.sk_ecdsa_sha2_nistp256,
        BuiltinSignatures.ed25519_cert,
        BuiltinSignatures.sk_ssh_ed25519);
}
