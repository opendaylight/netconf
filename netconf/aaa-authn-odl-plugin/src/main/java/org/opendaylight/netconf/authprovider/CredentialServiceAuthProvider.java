/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.authprovider;

import org.opendaylight.aaa.api.AuthenticationException;
import org.opendaylight.aaa.api.Claim;
import org.opendaylight.aaa.api.CredentialAuth;
import org.opendaylight.aaa.api.PasswordCredentials;
import org.opendaylight.netconf.auth.AuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * AuthProvider implementation delegating to AAA CredentialAuth&lt;PasswordCredentials&gt; instance.
 */
public final class CredentialServiceAuthProvider implements AuthProvider {
    private static final Logger LOG = LoggerFactory.getLogger(CredentialServiceAuthProvider.class);

    // FIXME CredentialAuth is generic and it causes warnings during compilation
    // Maybe there should be a PasswordCredentialAuth implements CredentialAuth<PasswordCredentials>
    private final CredentialAuth<PasswordCredentials> credService;

    public CredentialServiceAuthProvider(final CredentialAuth<PasswordCredentials> credService) {
        this.credService = credService;
    }

    /**
     * Authenticate user. This implementation tracks CredentialAuth&lt;PasswordCredentials&gt;
     * and delegates the decision to it.
     */
    @Override
    public boolean authenticated(final String username, final String password) {

        Claim claim;
        try {
            claim = credService.authenticate(new PasswordCredentialsWrapper(username, password));
        } catch (AuthenticationException e) {
            LOG.debug("Authentication failed for user '{}'", username, e);
            return false;
        }

        LOG.debug("Authentication result for user '{}' : {}", username, claim.domain());
        return true;
    }

    private static final class PasswordCredentialsWrapper implements PasswordCredentials {
        private final String username;
        private final String password;

        PasswordCredentialsWrapper(final String username, final String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public String username() {
            return username;
        }

        @Override
        public String password() {
            return password;
        }

        @Override
        public String domain() {
            // If this is left null, default "sdn" domain is assumed
            return null;
        }
    }
}
