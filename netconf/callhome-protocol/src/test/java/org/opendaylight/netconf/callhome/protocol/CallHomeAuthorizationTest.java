/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.junit.Test;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSessionImpl;

public class CallHomeAuthorizationTest {
    @Test
    public void anAuthorizationOfRejectedIsNotAllowed() {
        // given
        CallHomeAuthorization auth = CallHomeAuthorization.rejected();
        // expect
        assertFalse(auth.isServerAllowed());
    }

    @Test
    public void anAuthorizationOfRejectedCannotBeAppliedToASession() {
        // given
        CallHomeAuthorization auth = CallHomeAuthorization.rejected();
        // when
        final var ex = assertThrows(IllegalStateException.class, () -> auth.applyTo(mock(ClientSession.class)));
        assertEquals("Server is not allowed.", ex.getMessage());
    }

    @Test
    public void anAuthorizationOfAcceptanceIsAllowed() {
        // given
        String session = "some-session";
        String user = "some-user-name";
        ClientSessionImpl mockSession = mock(ClientSessionImpl.class);
        doNothing().when(mockSession).setUsername(user);

        // and
        CallHomeAuthorization auth = CallHomeAuthorization.serverAccepted(session, user).build();
        // when
        auth.applyTo(mockSession);
        // then
        assertTrue(auth.isServerAllowed());
    }

    @Test
    public void anAuthorizationOfAcceptanceCanBeAppliedToASession() {
        // given
        String session = "some-session";
        String user = "some-user-name";
        String pwd = "pwd1";
        KeyPair pair = new KeyPair(mock(PublicKey.class), mock(PrivateKey.class));
        ClientSessionImpl mockSession = mock(ClientSessionImpl.class);
        doNothing().when(mockSession).setUsername(user);
        doNothing().when(mockSession).addPasswordIdentity(pwd);
        doNothing().when(mockSession).addPublicKeyIdentity(pair);
        // and
        CallHomeAuthorization auth = CallHomeAuthorization.serverAccepted(session, user)
                .addPassword(pwd)
                .addClientKeys(pair)
                .build();
        // when
        auth.applyTo(mockSession);
        // then
        verify(mockSession, times(1)).addPasswordIdentity(anyString());
        verify(mockSession, times(1)).addPublicKeyIdentity(any(KeyPair.class));
    }
}
