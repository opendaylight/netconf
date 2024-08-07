/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.authprovider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.api.AuthenticationException;
import org.opendaylight.aaa.api.Claim;
import org.opendaylight.aaa.api.PasswordCredentialAuth;
import org.opendaylight.aaa.api.PasswordCredentials;
import org.opendaylight.netconf.auth.aaa.CredentialServiceAuthProvider;

@ExtendWith(MockitoExtension.class)
class CredentialServiceAuthProviderTest {
    @Mock
    private PasswordCredentialAuth credAuth;
    @Mock
    private Claim claim;

    private CredentialServiceAuthProvider authProvider;

    @BeforeEach
    void beforeEach() {
        authProvider = new CredentialServiceAuthProvider(credAuth);
    }

    @Test
    void testAuthenticatedTrue() {
        doReturn("domain").when(claim).domain();
        doReturn(claim).when(credAuth).authenticate(any(PasswordCredentials.class));

        assertTrue(authProvider.authenticateUser("user", "pwd"));
    }

    @Test
    void testAuthenticatedFalse() {
        doThrow(AuthenticationException.class).when(credAuth).authenticate(any(PasswordCredentials.class));

        assertFalse(authProvider.authenticateUser("user", "pwd"));
    }
}
