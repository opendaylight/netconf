/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.auth.aaa;

import org.opendaylight.aaa.api.PasswordCredentials;

final class DefaultPasswordCredentials implements PasswordCredentials {
    private final String username;
    private final String password;

    DefaultPasswordCredentials(final String username, final String password) {
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