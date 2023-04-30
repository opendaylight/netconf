/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.authprovider;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.aaa.api.AuthenticationException;
import org.opendaylight.aaa.api.Claim;
import org.opendaylight.aaa.api.PasswordCredentialAuth;
import org.opendaylight.aaa.api.PasswordCredentials;
import org.opendaylight.netconf.auth.aaa.CredentialServiceAuthProvider;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class CredentialServiceAuthProviderTest {
    @Mock
    private PasswordCredentialAuth credAuth;

    @Test
    public void testAuthenticatedTrue() throws Exception {
        Claim claim = mock(Claim.class);
        doReturn("domain").when(claim).domain();
        doReturn(claim).when(credAuth).authenticate(any(PasswordCredentials.class));

        CredentialServiceAuthProvider credentialServiceAuthProvider = new CredentialServiceAuthProvider(credAuth);
        assertTrue(credentialServiceAuthProvider.authenticated("user", "pwd"));
    }

    @Test
    public void testAuthenticatedFalse() throws Exception {
        doThrow(AuthenticationException.class).when(credAuth).authenticate(any(PasswordCredentials.class));
        CredentialServiceAuthProvider credentialServiceAuthProvider = new CredentialServiceAuthProvider(credAuth);
        assertFalse(credentialServiceAuthProvider.authenticated("user", "pwd"));
    }
}
