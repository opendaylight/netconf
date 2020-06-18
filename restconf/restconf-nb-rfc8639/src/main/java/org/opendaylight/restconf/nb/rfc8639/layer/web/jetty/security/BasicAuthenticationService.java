/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.security;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.security.auth.Credentials;
import org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.security.auth.psswd.UsernamePasswordCredentials;


@Singleton
public class BasicAuthenticationService implements AuthenticationService {

    @Inject
    private Users users;

    @Override
    public boolean authenticate(@NonNull final Credentials credentials) {
        if (credentials instanceof UsernamePasswordCredentials) {
            final String userName = (((UsernamePasswordCredentials) credentials).getUserName());
            final String password = (((UsernamePasswordCredentials) credentials).getPassword());
            if ((userName == null) || (password == null)) {
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            }
            return users.getUsers().stream()
                    .anyMatch(u -> (u.getUserName().equals(userName) && u.getPassword().equals(password)));
        } else {
            throw new UnsupportedOperationException("This credentials ["
                    + credentials.getClass().getName() + "] type is not supported");
        }
    }
}
