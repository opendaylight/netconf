/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev241016.SshPublicKeyAlgorithm.SshEd25519;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm.Aes256Cbc;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.DiffieHellmanGroupExchangeSha1;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm.HmacSha2256;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm.None;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.cipher.BuiltinCiphers;
import org.opendaylight.netconf.shaded.sshd.common.kex.BuiltinDHFactories;
import org.opendaylight.netconf.shaded.sshd.common.kex.KexProposalOption;
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.signature.BuiltinSignatures;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm;

@ExtendWith(MockitoExtension.class)
class NegotiatedAlgorithmsTest {
    @Mock
    private ClientSession session;

    @Test
    void testTranslationFromMapsFactoryNamesToIanaTypes() {
        doReturn(BuiltinDHFactories.dhgex.getName()).when(session)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);
        doReturn(BuiltinSignatures.ed25519.getName()).when(session)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn(BuiltinCiphers.aes256cbc.getName()).when(session)
            .getNegotiatedKexParameter(KexProposalOption.C2SENC);
        doReturn(BuiltinMacs.hmacsha256.getName()).when(session)
            .getNegotiatedKexParameter(KexProposalOption.C2SMAC);

        final var algs = NegotiatedAlgorithms.readFrom(session);
        // Each component must be the IANA-enum-backed union produced by the policy classes
        assertEquals(new SshKeyExchangeAlgorithm(DiffieHellmanGroupExchangeSha1), algs.keyExchange());
        assertEquals(new SshPublicKeyAlgorithm(SshEd25519), algs.hostKey());
        assertEquals(new SshEncryptionAlgorithm(Aes256Cbc), algs.encryption());
        assertEquals(new SshMacAlgorithm(HmacSha2256), algs.mac());
    }

    @Test
    void testTranslationOfAeadMacToNone() {
        // "aead" is returned by mina-sshd when an AEAD cipher (e.g. AES-GCM) is used; those ciphers provide
        // authentication inline so there is no separate MAC algorithm.
        doReturn(BuiltinDHFactories.dhgex.getName()).when(session)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);
        doReturn(BuiltinSignatures.ed25519.getName()).when(session)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn(BuiltinCiphers.cc20p1305_openssh.getName()).when(session)
            .getNegotiatedKexParameter(KexProposalOption.C2SENC);
        doReturn("aead").when(session).getNegotiatedKexParameter(KexProposalOption.C2SMAC);

        assertEquals(new SshMacAlgorithm(None), NegotiatedAlgorithms.readFrom(session).mac());
    }

    @ParameterizedTest
    @EnumSource(names = {"ALGORITHMS", "SERVERKEYS", "C2SENC", "C2SMAC"})
    void readFromLeavesComponentNullWhenParameterMissing(final KexProposalOption missing) {
        doReturn(missing == KexProposalOption.ALGORITHMS ? null : BuiltinDHFactories.dhgex.getName())
            .when(session).getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);
        doReturn(missing == KexProposalOption.SERVERKEYS ? null : BuiltinSignatures.ed25519.getName())
            .when(session).getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn(missing == KexProposalOption.C2SENC ? null : BuiltinCiphers.aes256cbc.getName())
            .when(session).getNegotiatedKexParameter(KexProposalOption.C2SENC);
        doReturn(missing == KexProposalOption.C2SMAC ? null : BuiltinMacs.hmacsha256.getName())
            .when(session).getNegotiatedKexParameter(KexProposalOption.C2SMAC);

        final var algs = NegotiatedAlgorithms.readFrom(session);
        // Only the missing parameter yields a null component; the others remain populated
        if (missing == KexProposalOption.ALGORITHMS) {
            assertNull(algs.keyExchange());
        } else {
            assertNotNull(algs.keyExchange());
        }
        if (missing == KexProposalOption.SERVERKEYS) {
            assertNull(algs.hostKey());
        } else {
            assertNotNull(algs.hostKey());
        }
        if (missing == KexProposalOption.C2SENC) {
            assertNull(algs.encryption());
        } else {
            assertNotNull(algs.encryption());
        }
        if (missing == KexProposalOption.C2SMAC) {
            assertNull(algs.mac());
        } else {
            assertNotNull(algs.mac());
        }
    }
}
