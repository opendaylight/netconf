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
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev241016.SshPublicKeyAlgorithm.SshEd25519;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm.Aes256Cbc;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.DiffieHellmanGroupExchangeSha1;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm.HmacSha2256;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opendaylight.netconf.client.mdsal.api.NegotiatedSshAlg;
import org.opendaylight.netconf.transport.ssh.NegotiatedAlgorithms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm;

class CallHomeSshServerAlgorithmTest {
    private static final SshKeyExchangeAlgorithm KEX = new SshKeyExchangeAlgorithm(DiffieHellmanGroupExchangeSha1);
    private static final SshPublicKeyAlgorithm HOST_KEY = new SshPublicKeyAlgorithm(SshEd25519);
    private static final SshEncryptionAlgorithm ENCRYPTION = new SshEncryptionAlgorithm(Aes256Cbc);
    private static final SshMacAlgorithm MAC = new SshMacAlgorithm(HmacSha2256);

    @ParameterizedTest
    @ValueSource(strings = {"keyExchange", "hostKey", "encryption", "mac"})
    void returnsNullWhenAnyAlgorithmMissing(final String missing) {
        // A missing value in any of the four negotiated algorithms must yield a null result
        final var algorithms = new NegotiatedAlgorithms(
            "keyExchange".equals(missing) ? null : KEX,
            "hostKey".equals(missing) ? null : HOST_KEY,
            "encryption".equals(missing) ? null : ENCRYPTION,
            "mac".equals(missing) ? null : MAC);

        assertNull(CallHomeSshServer.readNegotiatedAlg(algorithms), "missing " + missing);
    }

    @Test
    void mapsNegotiatedAlgorithms() {
        assertEquals(new NegotiatedSshAlg(KEX, HOST_KEY, ENCRYPTION, MAC),
            CallHomeSshServer.readNegotiatedAlg(new NegotiatedAlgorithms(KEX, HOST_KEY, ENCRYPTION, MAC)));
    }
}
