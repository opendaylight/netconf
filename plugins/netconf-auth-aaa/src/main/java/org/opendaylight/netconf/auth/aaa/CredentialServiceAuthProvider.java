/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.auth.aaa;

import static java.util.Objects.requireNonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.aaa.api.AuthenticationException;
import org.opendaylight.aaa.api.Claim;
import org.opendaylight.aaa.api.PasswordCredentialAuth;
import org.opendaylight.netconf.auth.AuthProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AuthProvider implementation delegating to a {@link PasswordCredentialAuth} instance.
 */
@Singleton
@Component(immediate = true, property = "type=netconf-auth-provider")
public final class CredentialServiceAuthProvider implements AuthProvider {
    private static final Logger LOG = LoggerFactory.getLogger(CredentialServiceAuthProvider.class);

    private final PasswordCredentialAuth credService;

    @Inject
    @Activate
    public CredentialServiceAuthProvider(final @Reference PasswordCredentialAuth credService) {
        this.credService = requireNonNull(credService);
    }

    /**
     * Authenticate user. This implementation tracks CredentialAuth&lt;PasswordCredentials&gt;
     * and delegates the decision to it.
     */
    @Override
    public boolean authenticated(final String username, final String password) {
        final var credentials = new DefaultPasswordCredentials(username, password);

        final Claim claim;
        try {
            claim = credService.authenticate(credentials);
        } catch (AuthenticationException e) {
            LOG.debug("Authentication failed for user '{}'", username, e);
            return false;
        }

        LOG.debug("Authentication result for user '{}' : {}", username, claim.domain());
        return true;
    }
}
