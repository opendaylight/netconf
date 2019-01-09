/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.web.auth;

import org.opendaylight.restconf.common.web.AuthWebInitializer;
import org.opendaylight.restconf.nb.rfc8040.web.Rfc8040WebRegistrar;

/**
 * Initializes the RFC8040 endpoint with authentication.
 */
public class WebInitializer extends AuthWebInitializer {
    public WebInitializer(Rfc8040WebRegistrar registrar) {
        super(registrar);
    }
}
