/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.bierman02.web.noauth;

import org.opendaylight.netconf.sal.restconf.web.Bierman02WebRegistrar;

/**
 * Initializes the bierman-02 endpoint without authentication.
 *
 * @author Thomas Pantelis
 */
public class WebInitializer {
    public WebInitializer(Bierman02WebRegistrar registrar) {
        registrar.registerWithoutAuthentication();
    }
}
