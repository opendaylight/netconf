/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev241016.SshPublicKeyAlgorithm.SshEd25519;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm.Aes256Cbc;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.DiffieHellmanGroupExchangeSha1;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm.HmacSha2256;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm.None;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opendaylight.netconf.shaded.sshd.common.cipher.BuiltinCiphers;
import org.opendaylight.netconf.shaded.sshd.common.kex.BuiltinDHFactories;
import org.opendaylight.netconf.shaded.sshd.common.kex.KexProposalOption;
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.signature.BuiltinSignatures;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm;

class NegotiatedAlgorithmsTest {
    @Test
    void ofMapsFactoryNamesToIanaTypes() {
        final var algs = NegotiatedAlgorithms.of(
            BuiltinDHFactories.dhgex.getName(),
            BuiltinSignatures.ed25519.getName(),
            BuiltinCiphers.aes256cbc.getName(),
            BuiltinMacs.hmacsha256.getName());
        // Each component must be the IANA-enum-backed union produced by the policy classes
        assertEquals(new SshKeyExchangeAlgorithm(DiffieHellmanGroupExchangeSha1), algs.keyExchange());
        assertEquals(new SshPublicKeyAlgorithm(SshEd25519), algs.hostKey());
        assertEquals(new SshEncryptionAlgorithm(Aes256Cbc), algs.encryption());
        assertEquals(new SshMacAlgorithm(HmacSha2256), algs.mac());
    }

    @Test
    void ofMapsAeadMacToNone() {
        // "aead" is returned by mina-sshd when an AEAD cipher (e.g. AES-GCM) is used; those ciphers provide
        // authentication inline so there is no separate MAC algorithm.
        final var algs = NegotiatedAlgorithms.of(
            BuiltinDHFactories.dhgex.getName(),
            BuiltinSignatures.ed25519.getName(),
            BuiltinCiphers.cc20p1305_openssh.getName(), "aead");
        assertEquals(new SshMacAlgorithm(None), algs.mac());
    }

    @ParameterizedTest
    @EnumSource(names = {"ALGORITHMS", "SERVERKEYS", "C2SENC", "C2SMAC"})
    void ofLeavesComponentNullWhenFactoryNameNull(final KexProposalOption missing) {
        final var algs = NegotiatedAlgorithms.of(
            missing == KexProposalOption.ALGORITHMS ? null : BuiltinDHFactories.dhgex.getName(),
            missing == KexProposalOption.SERVERKEYS ? null : BuiltinSignatures.ed25519.getName(),
            missing == KexProposalOption.C2SENC ? null : BuiltinCiphers.aes256cbc.getName(),
            missing == KexProposalOption.C2SMAC ? null : BuiltinMacs.hmacsha256.getName());

        // Only the missing parameter yields a null component; the others remain populated
        assertAll(
            () -> assertEquals(missing == KexProposalOption.ALGORITHMS, algs.keyExchange() == null),
            () -> assertEquals(missing == KexProposalOption.SERVERKEYS, algs.hostKey() == null),
            () -> assertEquals(missing == KexProposalOption.C2SENC, algs.encryption() == null),
            () -> assertEquals(missing == KexProposalOption.C2SMAC, algs.mac() == null));
    }
}
