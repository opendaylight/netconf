/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.security.auth.psswd;

import org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.security.auth.Credentials;

public final class UsernamePasswordCredentials implements Credentials {

    private String userName;
    private String password;

    public UsernamePasswordCredentials(final String userName, final String password) {
        this.userName = userName;
        this.password = password;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }
}
