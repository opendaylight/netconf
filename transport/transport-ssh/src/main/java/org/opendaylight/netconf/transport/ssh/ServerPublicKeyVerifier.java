/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.List;
import org.opendaylight.netconf.shaded.sshd.client.keyverifier.ServerKeyVerifier;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;

final class ServerPublicKeyVerifier implements ServerKeyVerifier {

    final List<PublicKey> publicKeys;

    ServerPublicKeyVerifier(final List<Certificate> certificates, final List<PublicKey> publicKeys) {
        requireNonNull(certificates);
        requireNonNull(publicKeys);
        this.publicKeys = ImmutableList.<PublicKey>builder().addAll(publicKeys)
                .addAll(certificates.stream().map(Certificate::getPublicKey).toList()).build();
    }

    @Override
    public boolean verifyServerKey(final ClientSession clientSession, final SocketAddress socketAddress,
            final PublicKey publicKey) {
        return publicKeys.contains(publicKey);
    }
}
