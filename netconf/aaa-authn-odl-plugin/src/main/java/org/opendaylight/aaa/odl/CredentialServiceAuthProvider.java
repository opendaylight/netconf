/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.aaa.odl;

import java.util.Map;
import org.opendaylight.aaa.api.AuthenticationException;
import org.opendaylight.aaa.api.Claim;
import org.opendaylight.aaa.api.CredentialAuth;
import org.opendaylight.aaa.api.PasswordCredentials;
import org.opendaylight.netconf.auth.AuthProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * AuthProvider implementation delegating to AAA CredentialAuth&lt;PasswordCredentials&gt; instance.
 */
public final class CredentialServiceAuthProvider implements AuthProvider, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(CredentialServiceAuthProvider.class);

    /**
     * Singleton instance with delayed instantiation
     */
    public static volatile Map.Entry<BundleContext, CredentialServiceAuthProvider> INSTANCE;

    // FIXME CredentialAuth is generic and it causes warnings during compilation
    // Maybe there should be a PasswordCredentialAuth implements CredentialAuth<PasswordCredentials>
    private volatile CredentialAuth<PasswordCredentials> nullableCredService;
    private final ServiceTracker<CredentialAuth, CredentialAuth> listenerTracker;

    public CredentialServiceAuthProvider(final BundleContext bundleContext) {

        final ServiceTrackerCustomizer<CredentialAuth, CredentialAuth> customizer = new ServiceTrackerCustomizer<CredentialAuth, CredentialAuth>() {
            @Override
            public CredentialAuth addingService(final ServiceReference<CredentialAuth> reference) {
                logger.trace("Credential service {} added", reference);
                nullableCredService = bundleContext.getService(reference);
                return nullableCredService;
            }

            @Override
            public void modifiedService(final ServiceReference<CredentialAuth> reference, final CredentialAuth service) {
                logger.trace("Replacing modified Credential service {}", reference);
                nullableCredService = service;
            }

            @Override
            public void removedService(final ServiceReference<CredentialAuth> reference, final CredentialAuth service) {
                logger.trace("Removing Credential service {}. This AuthProvider will fail to authenticate every time", reference);
                synchronized (CredentialServiceAuthProvider.this) {
                    nullableCredService = null;
                }
            }
        };
        listenerTracker = new ServiceTracker<>(bundleContext, CredentialAuth.class, customizer);
        listenerTracker.open();
    }

    /**
     * Authenticate user. This implementation tracks CredentialAuth&lt;PasswordCredentials&gt; and delegates the decision to it. If the service is not
     * available, IllegalStateException is thrown.
     */
    @Override
    public synchronized boolean authenticated(final String username, final String password) {
        if (nullableCredService == null) {
            logger.warn("Cannot authenticate user '{}', Credential service is missing", username);
            throw new IllegalStateException("Credential service is not available");
        }

        Claim claim;
        try {
            claim = nullableCredService.authenticate(new PasswordCredentialsWrapper(username, password));
        } catch (AuthenticationException e) {
            logger.debug("Authentication failed for user '{}' : {}", username, e);
            return false;
        }

        logger.debug("Authentication result for user '{}' : {}", username, claim.domain());
        return true;
    }

    /**
     * Invoke by blueprint
     * @throws Exception
     */
    @Override
    public void close() {
        listenerTracker.close();
        nullableCredService = null;
    }

    private static final class PasswordCredentialsWrapper implements PasswordCredentials {
        private final String username;
        private final String password;

        public PasswordCredentialsWrapper(final String username, final String password) {
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
