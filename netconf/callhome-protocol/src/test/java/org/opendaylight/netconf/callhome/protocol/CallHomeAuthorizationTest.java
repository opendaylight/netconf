/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.apache.sshd.ClientSession;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.junit.Ignore;
import org.junit.Test;


public class CallHomeAuthorizationTest
{
    @Test
    public void AnAuthorizationOfRejectedIsNotAllowed()
    {
        // given
        CallHomeAuthorization auth = CallHomeAuthorization.rejected();
        // expect
        assertFalse(auth.isServerAllowed());
    }

    @Test(expected = IllegalStateException.class)
    public void AnAuthorizationOfRejectedCannotBeAppliedToASession()
    {
        // given
        CallHomeAuthorization auth = CallHomeAuthorization.rejected();
        // when
        auth.applyTo(mock(ClientSession.class));
    }

    @Test
    @Ignore
    public void AnAuthorizationOfAcceptanceIsAllowed()
    {
        // given
        String session = "some-session";
        String user = "some-user-name";
        ClientSessionImpl mockSession = mock(ClientSessionImpl.class);
        // and
        CallHomeAuthorization auth = CallHomeAuthorization.serverAccepted(session, user).build();
        // when
        auth.applyTo(mockSession);
        // then
        assertTrue(auth.isServerAllowed());
    }

    @Test
    @Ignore
    public void AnAuthorizationOfAcceptanceCanBeAppliedToASession()
    {
        // given
        String session = "some-session";
        String user = "some-user-name";
        KeyPair pair = new KeyPair(mock(PublicKey.class), mock(PrivateKey.class));
        ClientSessionImpl mockSession = mock(ClientSessionImpl.class);
        // and
        CallHomeAuthorization auth = CallHomeAuthorization.serverAccepted(session, user)
                .addPassword("pwd1")
                .addClientKeys(pair)
                .build();
        // when
        auth.applyTo(mockSession);
        // then
        verify(mockSession, times(1)).addPasswordIdentity(anyString());
        verify(mockSession, times(1)).addPublicKeyIdentity(any(KeyPair.class));
    }

}
