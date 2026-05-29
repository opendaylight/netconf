/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev241016.SshPublicKeyAlgorithm.SshEd25519;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm.Aes256Cbc;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm.Chacha20Poly1305;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.DiffieHellmanGroupExchangeSha1;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm.HmacSha2256;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm.None;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.client.mdsal.api.NegotiatedSshAlg;
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
class CallHomeSshServerAlgorithmTest {
    @Mock
    private ClientSession clientSession;

    @ParameterizedTest
    @EnumSource(names = {"ALGORITHMS", "SERVERKEYS", "C2SENC", "C2SMAC"})
    void returnsNullWhenAnyParamMissing(final KexProposalOption missing) {
        // A missing value in any of the four negotiated parameters must yield a null result
        doReturn(missing == KexProposalOption.ALGORITHMS ? null : BuiltinDHFactories.dhgex.getName())
            .when(clientSession).getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);
        doReturn(missing == KexProposalOption.SERVERKEYS ? null : BuiltinSignatures.ed25519.getName())
            .when(clientSession).getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn(missing == KexProposalOption.C2SENC ? null : BuiltinCiphers.aes256cbc.getName())
            .when(clientSession).getNegotiatedKexParameter(KexProposalOption.C2SENC);
        doReturn(missing == KexProposalOption.C2SMAC ? null : BuiltinMacs.hmacsha256.getName())
            .when(clientSession).getNegotiatedKexParameter(KexProposalOption.C2SMAC);

        assertNull(CallHomeSshServer.readNegotiatedAlg(clientSession), "missing " + missing);
    }

    @Test
    void mapsFactoryNamesToIanaTypes() {
        doReturn(BuiltinDHFactories.dhgex.getName()).when(clientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);
        doReturn(BuiltinSignatures.ed25519.getName()).when(clientSession)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn(BuiltinCiphers.aes256cbc.getName()).when(clientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SENC);
        doReturn(BuiltinMacs.hmacsha256.getName()).when(clientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SMAC);

        assertEquals(new NegotiatedSshAlg(
            new SshKeyExchangeAlgorithm(DiffieHellmanGroupExchangeSha1),
            new SshPublicKeyAlgorithm(SshEd25519),
            new SshEncryptionAlgorithm(Aes256Cbc),
            new SshMacAlgorithm(HmacSha2256)),
            CallHomeSshServer.readNegotiatedAlg(clientSession));
    }

    @Test
    void mapsAeadMacToNone() {
        // mina-sshd reports "aead" as the MAC when an AEAD cipher (e.g. ChaCha20-Poly1305) is negotiated;
        // such ciphers provide authentication inline so there is no separate MAC algorithm.
        doReturn(BuiltinDHFactories.dhgex.getName()).when(clientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);
        doReturn(BuiltinSignatures.ed25519.getName()).when(clientSession)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn(BuiltinCiphers.cc20p1305_openssh.getName()).when(clientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SENC);
        doReturn("aead").when(clientSession).getNegotiatedKexParameter(KexProposalOption.C2SMAC);

        // "aead" must be mapped to SshMacAlgorithm.None, not passed through as a raw string
        assertEquals(new NegotiatedSshAlg(
            new SshKeyExchangeAlgorithm(DiffieHellmanGroupExchangeSha1),
            new SshPublicKeyAlgorithm(SshEd25519),
            new SshEncryptionAlgorithm(Chacha20Poly1305),
            new SshMacAlgorithm(None)),
            CallHomeSshServer.readNegotiatedAlg(clientSession));
    }
}
