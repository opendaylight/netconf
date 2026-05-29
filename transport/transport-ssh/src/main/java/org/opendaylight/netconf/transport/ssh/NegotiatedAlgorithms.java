/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
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
public record NegotiatedAlgorithms(
        @Nullable SshKeyExchangeAlgorithm keyExchange,
        @Nullable SshPublicKeyAlgorithm hostKey,
        @Nullable SshEncryptionAlgorithm encryption,
        @Nullable SshMacAlgorithm mac) {
    /**
     * Maps the mina-sshd factory names of the negotiated algorithms to their IANA YANG representation. Individual
     * components are null when the corresponding factory name is null, i.e. the parameter has not been negotiated.
     *
     * @param keyExchange negotiated key exchange factory name, or null
     * @param hostKey negotiated server host key factory name, or null
     * @param encryption negotiated encryption factory name, or null
     * @param mac negotiated mac factory name, or null
     * @return the negotiated algorithms
     */
    public static @NonNull NegotiatedAlgorithms of(final @Nullable String keyExchange, final @Nullable String hostKey,
            final @Nullable String encryption, final @Nullable String mac) {
        return new NegotiatedAlgorithms(
            KeyExchangePolicy.CLIENT.algOf(keyExchange),
            PublicKeyPolicy.INSTANCE.algOf(hostKey),
            EncryptionPolicy.INSTANCE.algOf(encryption),
            MacPolicy.INSTANCE.algOf(mac));
    }
}
