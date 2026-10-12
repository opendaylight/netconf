/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientAuthHostBased;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientAuthWithPassword;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientAuthWithPublicKey;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientIdentityHostBasedCentral;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientIdentityWithPassword;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientIdentityWithPublicKeyCentral;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildServerAuthWithPublicKey;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildServerIdentityWithCentralKeystore;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildServerIdentityWithKeyPair;
import static org.opendaylight.netconf.transport.ssh.TestUtils.generateKeyPairWithCertificate;
import static org.opendaylight.netconf.transport.ssh.TestUtils.toKeyPair;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.opendaylight.netconf.keystore.api.KeystoreAccess;
import org.opendaylight.netconf.transport.api.TransportChannel;

/**
 * SSH connections where keys are configured as a central keystore reference and resolved through
 * {@link KeystoreAccess}.
 */
class CentralKeystoreTest extends AbstractClientServerTest {
    private static final String KEY_NAME = "central-key";

    private static SSHTransportStackFactory FACTORY;

    @Mock
    private KeystoreAccess keystoreAccess;

    @BeforeAll
    static void beforeAll() {
        FACTORY = new SSHTransportStackFactory("CentralKeystoreTest", 0);
    }

    @AfterAll
    static void afterAll() {
        FACTORY.close();
    }

    @Test
    void serverHostKeyFromCentralKeystore() throws Exception {
        final var keyData = generateKeyPairWithCertificate(RSA);
        when(keystoreAccess.lookupAsymmetric(KEY_NAME)).thenReturn(toKeyPair(keyData));

        final var username = getUsernameAndUpdate();
        when(sshServerConfig.getServerIdentity()).thenReturn(buildServerIdentityWithCentralKeystore(KEY_NAME));
        when(sshServerConfig.getClientAuthentication())
            .thenReturn(buildClientAuthWithPassword(username, "$0$" + PASSWORD));
        when(sshClientConfig.getClientIdentity()).thenReturn(buildClientIdentityWithPassword(username, PASSWORD));
        // the client accepts only the host key stored in the keystore
        when(sshClientConfig.getServerAuthentication()).thenReturn(buildServerAuthWithPublicKey(keyData));

        assertConnected(keystoreAccess, KeystoreAccess.empty());
    }

    @Test
    void clientPublicKeyFromCentralKeystore() throws Exception {
        final var keyData = generateKeyPairWithCertificate(RSA);
        when(keystoreAccess.lookupAsymmetric(KEY_NAME)).thenReturn(toKeyPair(keyData));

        final var username = getUsernameAndUpdate();
        when(sshServerConfig.getServerIdentity())
            .thenReturn(buildServerIdentityWithKeyPair(generateKeyPairWithCertificate(RSA)));
        when(sshServerConfig.getClientAuthentication()).thenReturn(buildClientAuthWithPublicKey(username, keyData));
        when(sshClientConfig.getClientIdentity())
            .thenReturn(buildClientIdentityWithPublicKeyCentral(username, KEY_NAME));
        when(sshClientConfig.getServerAuthentication()).thenReturn(null);

        assertConnected(KeystoreAccess.empty(), keystoreAccess);
    }

    @Test
    void clientHostBasedKeyFromCentralKeystore() throws Exception {
        final var keyData = generateKeyPairWithCertificate(RSA);
        when(keystoreAccess.lookupAsymmetric(KEY_NAME)).thenReturn(toKeyPair(keyData));

        final var username = getUsernameAndUpdate();
        when(sshServerConfig.getServerIdentity())
            .thenReturn(buildServerIdentityWithKeyPair(generateKeyPairWithCertificate(RSA)));
        when(sshServerConfig.getClientAuthentication()).thenReturn(buildClientAuthHostBased(username, keyData));
        when(sshClientConfig.getClientIdentity()).thenReturn(buildClientIdentityHostBasedCentral(username, KEY_NAME));
        when(sshClientConfig.getServerAuthentication()).thenReturn(null);

        assertConnected(KeystoreAccess.empty(), keystoreAccess);
    }

    private void assertConnected(final KeystoreAccess serverKeystore, final KeystoreAccess clientKeystore)
            throws Exception {
        final var server = FACTORY.listenServer(SUBSYSTEM, serverListener, tcpServerConfig, sshServerConfig, null,
            serverKeystore).get(2, TimeUnit.SECONDS);
        try {
            final var client = FACTORY.connectClient(SUBSYSTEM, clientListener, algListener, tcpClientConfig,
                sshClientConfig, null, clientKeystore).get(2, TimeUnit.SECONDS);
            try {
                // the transport channels are established only after successful authentication
                verify(serverListener, timeout(10_000)).onTransportChannelEstablished(any(TransportChannel.class));
                verify(clientListener, timeout(10_000)).onTransportChannelEstablished(any(TransportChannel.class));
            } finally {
                client.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
        }
    }
}
