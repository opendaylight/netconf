/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm;

/**
 * Set of a negotiated transport algorithms.
 *
 * @param negotiatedKexAlg negotiated key exchange algorithm
 * @param negotiatedHostKeyAlg negotiated server host key algorithm
 * @param negotiatedEncryptionAlg negotiated encryption algorithm
 * @param negotiatedMacAlg negotiated mac algorithm
 */
public record NegotiatedSshAlg(
    SshKeyExchangeAlgorithm negotiatedKexAlg,
    SshPublicKeyAlgorithm negotiatedHostKeyAlg,
    SshEncryptionAlgorithm negotiatedEncryptionAlg,
    SshMacAlgorithm negotiatedMacAlg) {
}
