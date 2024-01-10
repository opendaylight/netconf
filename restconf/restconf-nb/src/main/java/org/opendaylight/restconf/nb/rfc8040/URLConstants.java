/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Various constants related to the layout of URLs on which the implementation resides.
 */
@NonNullByDefault
public final class URLConstants {
    /**
     * The second URL path element for streams support, i.e. {@code https://localhost/BASE_PATH/STREAMS}.
     */
    public static final String STREAMS_SUBPATH = "streams";
    /**
     * The second URL path element for YANG library module support, i.e. {@code https://localhost/BASE_PATH/MODULES}.
     */
    public static final String MODULES_SUBPATH = "modules";
    /**
     * The query parameter carrying the optional revision in YANG library module support, i.e.
     * {@code https://localhost/BASE_PATH/MODULES?REVISION=2023-11-26}.
     */
    public static final String MODULES_REVISION_QUERY = "revision";

    private URLConstants() {
        // Hidden on purpose
    }
}