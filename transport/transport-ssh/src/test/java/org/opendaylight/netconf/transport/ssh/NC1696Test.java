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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.shaded.sshd.client.SshClient;
import org.opendaylight.netconf.shaded.sshd.client.keyverifier.ServerKeyVerifier;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.AttributeRepository.AttributeKey;

/**
 * Tests the server key check of {@link SessionHandlerConfigurator} on a repeated key exchange.
 *
 * <p>{@link SSHClientSessionHandlerTest#reExchangeKeys()} covers a real server which presents the same key again. The
 * server in that test cannot change its key, so this test calls the verifier directly, to check that the same key is
 * accepted again and that a server which changes its key is rejected.
 */
@ExtendWith(MockitoExtension.class)
class NC1696Test {
    @Mock
    private SSHClientSessionHandler handler;
    @Mock
    private ClientSession session;

    private ServerKeyVerifier verifier;
    private PublicKey firstKey;
    private PublicKey secondKey;

    @BeforeEach
    void beforeEach() throws Exception {
        final var client = new SshClient();
        new SessionHandlerConfigurator(handler).configureClientFactoryManager(client);
        verifier = client.getServerKeyVerifier();

        final var keyPairGenerator = KeyPairGenerator.getInstance("EC");
        firstKey = keyPairGenerator.generateKeyPair().getPublic();
        secondKey = keyPairGenerator.generateKeyPair().getPublic();

        doReturn(new SSHClientCredentials("user", List.of(AbstractClientServerTest.PASSWORD), List.of()))
            .when(handler)
            .checkServerKey(any(SSHClientSessionRef.class), any(PublicKey.class), any(NegotiatedAlgorithms.class));
        // the session has no accepted key at the first key exchange, then it has the first key
        doReturn(null).doReturn(firstKey).when(session).getAttribute(any(AttributeKey.class));
    }

    @Test
    void sameServerKeyIsAcceptedTest() {
        assertTrue(verifier.verifyServerKey(session, null, firstKey));
        // the same key at a later key exchange is accepted without asking the handler again
        assertTrue(verifier.verifyServerKey(session, null, firstKey));
        verify(handler).checkServerKey(any(SSHClientSessionRef.class), any(PublicKey.class),
            any(NegotiatedAlgorithms.class));
    }

    @Test
    void changedServerKeyIsRejectedTest() {
        assertTrue(verifier.verifyServerKey(session, null, firstKey));
        assertFalse(verifier.verifyServerKey(session, null, secondKey));
        verify(handler).checkServerKey(any(SSHClientSessionRef.class), any(PublicKey.class),
            any(NegotiatedAlgorithms.class));
    }
}
