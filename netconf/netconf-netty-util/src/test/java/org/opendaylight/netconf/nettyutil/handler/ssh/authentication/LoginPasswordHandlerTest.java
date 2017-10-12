/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil.handler.ssh.authentication;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.session.ClientSession;
import org.junit.Test;

public class LoginPasswordHandlerTest {

    @Test
    public void testLoginPassword() throws Exception {
        final LoginPasswordHandler loginPasswordHandler = new LoginPasswordHandler("user", "pwd");
        assertEquals("user", loginPasswordHandler.getUsername());

        final ClientSession session = mock(ClientSession.class);
        doNothing().when(session).addPasswordIdentity("pwd");
        doReturn(mock(AuthFuture.class)).when(session).auth();
        loginPasswordHandler.authenticate(session);

        verify(session).addPasswordIdentity("pwd");
        verify(session).auth();
    }
}