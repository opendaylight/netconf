/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.aaa.odl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opendaylight.aaa.api.AuthenticationException;
import org.opendaylight.aaa.api.Claim;
import org.opendaylight.aaa.api.CredentialAuth;
import org.opendaylight.aaa.api.PasswordCredentials;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

public class CredentialServiceAuthProviderTest {

    @Mock
    private CredentialAuth<PasswordCredentials> credAuth;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void testAuthenticatedTrue() throws Exception {
        ServiceReference serviceRef = mock(ServiceReference.class);

        ServiceListenerAnswer answer = new ServiceListenerAnswer();

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

    private static class ServiceListenerAnswer implements Answer {

        ServiceListener serviceListener;

        @Override
        public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
            serviceListener = (ServiceListener) invocationOnMock.getArguments()[0];
            return null;
        }
    }
}
