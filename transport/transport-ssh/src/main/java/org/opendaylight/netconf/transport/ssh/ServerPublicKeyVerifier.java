/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.collect.ImmutableList;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.List;
import java.util.stream.Stream;
import org.opendaylight.netconf.shaded.sshd.client.keyverifier.ServerKeyVerifier;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.config.keys.KeyUtils;

final class ServerPublicKeyVerifier implements ServerKeyVerifier {
    private final List<PublicKey> allowedKeys;

    ServerPublicKeyVerifier(final List<Certificate> certificates, final List<PublicKey> publicKeys) {
        allowedKeys = Stream.concat(publicKeys.stream(), certificates.stream().map(Certificate::getPublicKey))
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public boolean verifyServerKey(final ClientSession clientSession, final SocketAddress socketAddress,
            final PublicKey publicKey) {
        return allowedKeys.stream().anyMatch(allowedKey -> KeyUtils.compareKeys(allowedKey, publicKey));
    }

    @Override
    public String toString() {
        return "ServerPublicKeyVerifier [allowedKeys=" + allowedKeys.size() + "]";
    }
}
