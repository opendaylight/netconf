/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientAuthWithPassword;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientAuthWithPublicKey;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildServerIdentityWithKeyPair;
import static org.opendaylight.netconf.transport.ssh.TestUtils.generateKeyPairWithCertificate;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.ssh.TestUtils.KeyData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.ssh.server.grouping.ClientAuthentication;

class SSHClientSessionHandlerTest extends AbstractClientServerTest {
    private static SSHTransportStackFactory FACTORY;

    @Mock
    private SSHClientSessionHandler handler;
    @Captor
    private ArgumentCaptor<SSHClientSessionRef> checkedSessionCaptor;
    @Captor
    private ArgumentCaptor<SSHClientSessionRef> closedSessionCaptor;
    @Captor
    private ArgumentCaptor<PublicKey> serverKeyCaptor;
    @Captor
    private ArgumentCaptor<NegotiatedAlgorithms> algorithmsCaptor;
    @Captor
    private ArgumentCaptor<TransportChannel> clientTransportChannelCaptor;

    @BeforeAll
    static void beforeAll() {
        FACTORY = new SSHTransportStackFactory("SessionHandlerTest", 0);
    }

    @AfterAll
    static void afterAll() {
        FACTORY.close();
    }

    @Test
    void acceptServerWithPassword() throws Exception {
        final var username = getUsernameAndUpdate();
        assertServerAccepted(buildClientAuthWithPassword(username, "$0$" + PASSWORD),
            new SSHClientCredentials(username, List.of(PASSWORD), List.of()));
    }

    @Test
    void acceptServerWithPublicKey() throws Exception {
        final var username = getUsernameAndUpdate();
        final var clientKeyData = generateKeyPairWithCertificate(RSA);
        assertServerAccepted(buildClientAuthWithPublicKey(username, clientKeyData),
            new SSHClientCredentials(username, List.of(), List.of(keyPairOf(clientKeyData))));
    }

    @Test
    void rejectServer() throws Exception {
        final var username = getUsernameAndUpdate();
        doReturn(null).when(handler)
            .checkServerKey(any(SSHClientSessionRef.class), any(PublicKey.class), any(NegotiatedAlgorithms.class));

        final var client = listenClient();
        try {
            final var server = connectServer(generateKeyPairWithCertificate(RSA),
                buildClientAuthWithPassword(username, "$0$" + PASSWORD));
            try {
                verify(handler, timeout(10_000)).sessionClosed(closedSessionCaptor.capture());
                assertFalse(closedSessionCaptor.getValue().isAuthenticated());
                verify(clientListener, after(1_000).never()).onTransportChannelEstablished(any(TransportChannel.class));
            } finally {
                server.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            client.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void closeSession() throws Exception {
        final var username = getUsernameAndUpdate();
        doReturn(new SSHClientCredentials(username, List.of(PASSWORD), List.of())).when(handler)
            .checkServerKey(any(SSHClientSessionRef.class), any(PublicKey.class), any(NegotiatedAlgorithms.class));

        final var client = listenClient();
        try {
            final var server = connectServer(generateKeyPairWithCertificate(RSA),
                buildClientAuthWithPassword(username, "$0$" + PASSWORD));
            try {
                verify(clientListener, timeout(10_000)).onTransportChannelEstablished(any(TransportChannel.class));
                verify(handler).checkServerKey(checkedSessionCaptor.capture(), any(PublicKey.class),
                    any(NegotiatedAlgorithms.class));

                // closing the session from the client side is reported as well
                checkedSessionCaptor.getValue().close();
                verify(handler, timeout(10_000)).sessionClosed(closedSessionCaptor.capture());
                assertEquals(checkedSessionCaptor.getValue(), closedSessionCaptor.getValue());
            } finally {
                server.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            client.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void reExchangeKeys() throws Exception {
        final var username = getUsernameAndUpdate();
        doReturn(new SSHClientCredentials(username, List.of(PASSWORD), List.of())).when(handler)
            .checkServerKey(any(SSHClientSessionRef.class), any(PublicKey.class), any(NegotiatedAlgorithms.class));

        final var client = listenClient();
        try {
            final var server = connectServer(generateKeyPairWithCertificate(RSA),
                buildClientAuthWithPassword(username, "$0$" + PASSWORD));
            try {
                verify(clientListener, timeout(10_000)).onTransportChannelEstablished(any(TransportChannel.class));

                // sshd checks the server key again at the end of the repeated key exchange
                final var session = client.getSessions().iterator().next();
                session.reExchangeKeys().verify(10, TimeUnit.SECONDS);

                // the handler is asked only for the first key exchange, and the session stays open
                verify(handler).checkServerKey(any(SSHClientSessionRef.class), any(PublicKey.class),
                    any(NegotiatedAlgorithms.class));
                assertTrue(session.isOpen());
                verify(handler, never()).sessionClosed(any(SSHClientSessionRef.class));
            } finally {
                server.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            client.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    private void assertServerAccepted(final ClientAuthentication serverClientAuth,
            final SSHClientCredentials credentials) throws Exception {
        doReturn(credentials).when(handler)
            .checkServerKey(any(SSHClientSessionRef.class), any(PublicKey.class), any(NegotiatedAlgorithms.class));

        final var serverKeyData = generateKeyPairWithCertificate(RSA);
        final var client = listenClient();
        try {
            final var server = connectServer(serverKeyData, serverClientAuth);
            try {
                verify(clientListener, timeout(10_000))
                    .onTransportChannelEstablished(clientTransportChannelCaptor.capture());
                verify(handler).checkServerKey(checkedSessionCaptor.capture(), serverKeyCaptor.capture(),
                    algorithmsCaptor.capture());
                assertArrayEquals(serverKeyData.publicKeyBytes(), serverKeyCaptor.getValue().getEncoded());

                final var algorithms = algorithmsCaptor.getValue();
                assertNotNull(algorithms.keyExchange());
                assertNotNull(algorithms.hostKey());
                assertNotNull(algorithms.encryption());
                assertNotNull(algorithms.mac());

                final var session = checkedSessionCaptor.getValue();
                assertTrue(session.isAuthenticated());
                final var channel = clientTransportChannelCaptor.getValue().channel();
                assertEquals(channel.remoteAddress(), session.remoteAddress());
                verify(handler, never()).sessionClosed(any(SSHClientSessionRef.class));
            } finally {
                server.shutdown().get(2, TimeUnit.SECONDS);
            }
            // the closed session is the one which was checked
            verify(handler, timeout(10_000)).sessionClosed(closedSessionCaptor.capture());
            assertEquals(checkedSessionCaptor.getValue(), closedSessionCaptor.getValue());
        } finally {
            client.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    // The client has no client identity. The username comes from the handler for each session.
    private SSHClient listenClient() throws Exception {
        return FACTORY.listenClient(SUBSYSTEM, clientListener, tcpServerConfig, handler).get(2, TimeUnit.SECONDS);
    }

    private SSHServer connectServer(final KeyData serverKeyData, final ClientAuthentication clientAuth)
            throws Exception {
        doReturn(buildServerIdentityWithKeyPair(serverKeyData)).when(sshServerConfig).getServerIdentity();
        doReturn(clientAuth).when(sshServerConfig).getClientAuthentication();
        return FACTORY.connectServer(SUBSYSTEM, serverListener, tcpClientConfig, sshServerConfig)
            .get(2, TimeUnit.SECONDS);
    }

    private static KeyPair keyPairOf(final KeyData keyData) throws Exception {
        final var keyFactory = KeyFactory.getInstance(keyData.algorithm());
        return new KeyPair(keyFactory.generatePublic(new X509EncodedKeySpec(keyData.publicKeyBytes())),
            keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyData.privateKeyBytes())));
    }
}
