/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.security;

import java.util.List;
import org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.security.auth.psswd.UsernamePasswordCredentials;

public final class Users {
    private List<UsernamePasswordCredentials> users;

    public Users(final List<UsernamePasswordCredentials> users) {
        this.users = users;
    }

    public List<UsernamePasswordCredentials> getUsers() {
        return users;
    }
}
