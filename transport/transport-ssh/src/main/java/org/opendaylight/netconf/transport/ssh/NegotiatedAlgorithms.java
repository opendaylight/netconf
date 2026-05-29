/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.kex.KexProposalOption;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm;

/**
 * Algorithms negotiated on an SSH client session, expressed as their IANA YANG representation.
 *
 * @param keyExchange negotiated key exchange algorithm, or null if not negotiated
 * @param hostKey negotiated server host key algorithm, or null if not negotiated
 * @param encryption negotiated encryption algorithm, or null if not negotiated
 * @param mac negotiated mac algorithm, or null if not negotiated
 */
@NonNullByDefault
public record NegotiatedAlgorithms(
        @Nullable SshKeyExchangeAlgorithm keyExchange,
        @Nullable SshPublicKeyAlgorithm hostKey,
        @Nullable SshEncryptionAlgorithm encryption,
        @Nullable SshMacAlgorithm mac) {
    /**
     * Reads the algorithms negotiated on a client session and maps the mina-sshd factory names to their IANA YANG
     * representation. Individual components are null when the corresponding parameter has not been negotiated.
     *
     * @param session the client session
     * @return the negotiated algorithms
     */
    public static NegotiatedAlgorithms readFrom(final ClientSession session) {
        return new NegotiatedAlgorithms(
            KeyExchangePolicy.CLIENT.algOf(session.getNegotiatedKexParameter(KexProposalOption.ALGORITHMS)),
            PublicKeyPolicy.INSTANCE.algOf(session.getNegotiatedKexParameter(KexProposalOption.SERVERKEYS)),
            EncryptionPolicy.INSTANCE.algOf(session.getNegotiatedKexParameter(KexProposalOption.C2SENC)),
            MacPolicy.INSTANCE.algOf(session.getNegotiatedKexParameter(KexProposalOption.C2SMAC)));
    }
}
