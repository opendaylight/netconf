/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

/**
 * An exception occurring in the context of RESTCONF invocation.
 */
public final class RestconfServerException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public RestconfServerException(final String message, final Exception cause) {
        super(requireNonNull(message), cause);
    }
}
