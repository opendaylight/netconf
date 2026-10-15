/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.shaded.sshd.client.SshClient;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.AttributeRepository.AttributeKey;

/**
 * Tests the server key check of {@link SessionHandlerConfigurator} on a repeated key exchange.
 * {@link SSHClientSessionHandlerTest#reExchangeKeys()} covers a server which presents the same key again. The server
 * in that test cannot change its key, so this test calls the verifier directly with a second, different key, to check
 * that a server which changes its key is rejected.
 */
@ExtendWith(MockitoExtension.class)
class NC1696Test {
    @Mock
    private SSHClientSessionHandler handler;
    @Mock
    private ClientSession session;

    @Test
    void changedServerKeyIsRejectedTest() throws Exception {
        final var client = new SshClient();
        new SessionHandlerConfigurator(handler).configureClientFactoryManager(client);
        final var verifier = client.getServerKeyVerifier();

        final var keyPairGenerator = KeyPairGenerator.getInstance("EC");
        final var firstKey = keyPairGenerator.generateKeyPair().getPublic();
        final var secondKey = keyPairGenerator.generateKeyPair().getPublic();

        doReturn(new SSHClientCredentials("user", List.of(AbstractClientServerTest.PASSWORD), List.of()))
            .when(handler)
            .checkServerKey(any(SSHClientSessionRef.class), any(PublicKey.class), any(NegotiatedAlgorithms.class));
        // the session has no accepted key at the first key exchange, then it has the first key
        doReturn(null).doReturn(firstKey).when(session).getAttribute(any(AttributeKey.class));

        assertTrue(verifier.verifyServerKey(session, null, firstKey));
        assertFalse(verifier.verifyServerKey(session, null, secondKey));
        verify(handler).checkServerKey(any(SSHClientSessionRef.class), any(PublicKey.class),
            any(NegotiatedAlgorithms.class));
    }
}
