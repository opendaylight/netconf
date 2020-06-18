/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.security;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.security.auth.Credentials;

/**
 * AuthenticationService provides interface for custom authentication implementations.
 */
public interface AuthenticationService {

    /**
     * Authenticate returns true if user is authenticated.
     *
     * @param credentials Authentication credentials
     * @return true if authenticated.
     */
    boolean authenticate(@NonNull Credentials credentials);
}
