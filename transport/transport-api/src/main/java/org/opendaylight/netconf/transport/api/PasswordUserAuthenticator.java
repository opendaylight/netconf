/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2024 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A {@link UserAuthenticator} based on plaintext passwords.
 */
@NonNullByDefault
public non-sealed interface PasswordUserAuthenticator extends UserAuthenticator {
    /**
     * Authenticate user by username/password.
     *
     * @param username username
     * @param password password
     * @return true if authentication is successful, false otherwise
     */
    boolean authenticateUser(String username, String password);
}
