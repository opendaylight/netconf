/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.web;

import com.google.common.annotations.Beta;

/**
 * Initializes a {@link WebRegistrar} without authentication.
 *
 * @author Thomas Pantelis
 */
@Beta
public class NoAuthWebInitializer {
    public NoAuthWebInitializer(WebRegistrar registrar) {
        registrar.registerWithoutAuthentication();
    }
}
