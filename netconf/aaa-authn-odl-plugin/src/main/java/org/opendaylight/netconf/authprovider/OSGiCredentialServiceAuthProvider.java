/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.authprovider;

import org.opendaylight.aaa.api.PasswordCredentialAuth;
import org.opendaylight.netconf.auth.AuthProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, property = "type=netconf-auth-provider")
// FIXME: merge with CredentialServiceAuthProvider once we have OSGi R7
public final class OSGiCredentialServiceAuthProvider implements AuthProvider {
    @Reference
    PasswordCredentialAuth credService;

    private CredentialServiceAuthProvider delegate = null;

    @Override
    public boolean authenticated(final String username, final String password) {
        return delegate.authenticated(username, password);
    }

    @Activate
    void activate() {
        delegate = new CredentialServiceAuthProvider(credService);
    }

    @Deactivate
    void deactivate() {
        delegate = null;
    }
}
