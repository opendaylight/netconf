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
     * The first element URL path element for RESTCONF implementation, i.e. {@code https://localhost/BASE_PATH}.
     */
    public static final String BASE_PATH = "rests";
    /**
     * The second element for Server Sent Events support, i.e. {@code https://localhost/BASE_PATH/NOTIF}.
     */
    public static final String SSE_SUBPATH = "notif";

    private URLConstants() {
        // Hidden on purpose
    }
}