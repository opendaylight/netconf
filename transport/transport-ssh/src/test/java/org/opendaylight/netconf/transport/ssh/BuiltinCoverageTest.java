/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertAllAsValue(EncryptionAlgorithms.BY_YANG, BuiltinCiphers.values());
    }

    @Test
    void coveredBuiltinMacs() {
        assertAllAsValue(MacAlgorithms.BY_YANG, BuiltinMacs.values(),
            // These three are Encrypt-then-MAC modes as described in https://api.libssh.org/rfc/PROTOCOL
            // FIXME: we should provide these extensions
            BuiltinMacs.hmacsha1etm,
            BuiltinMacs.hmacsha256etm,
            BuiltinMacs.hmacsha512etm);
    }

    @Test
    @SuppressWarnings("deprecation")
    void coveredBuiltinSignatures() {
        assertAllAsValue(PublicKeyAlgorithms.BY_YANG, BuiltinSignatures.values(),
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

    // The meat of assertions. Yes we could use Parameterized tests, but this way is less meta.
    @SafeVarargs
    private static <T> void assertAllAsValue(final Map<?, ? super T> map, final T[] values, final T... suppressions) {
        final var unsuppressed = new HashSet<>(List.of(suppressions));
        final var unmatched = new ArrayList<T>();

        for (var alg : values) {
            if (!map.containsValue(alg) && !unsuppressed.remove(alg)) {
                unmatched.add(alg);
            }
        }

        assertEquals(List.of(), unmatched, "Unused SSHD builtins");
        assertEquals(Set.of(), unsuppressed, "Unused builtin suppressions");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource
    void coveredBuiltinDHFactories(final BuiltinKeyExchangePolicy policy) {
        final var unsuppressed = new HashSet<>(List.of());
        final var unmatched = new ArrayList<BuiltinDHFactories>();

        for (var alg : BuiltinDHFactories.values()) {
            if (policy.allFactories().noneMatch(f -> alg.getName().equals(f.getName())) && !unsuppressed.remove(alg)) {
                unmatched.add(alg);
            }
        }

        assertEquals(List.of(), unmatched, "Unused SSHD builtins");
        assertEquals(Set.of(), unsuppressed, "Unused builtin suppressions");
    }

    private static List<Arguments> coveredBuiltinDHFactories() {
        return List.of(
            arguments(named("client KEXs", BuiltinKeyExchangePolicy.CLIENT)),
            arguments(named("server KEXs", BuiltinKeyExchangePolicy.SERVER)));
    }
}
