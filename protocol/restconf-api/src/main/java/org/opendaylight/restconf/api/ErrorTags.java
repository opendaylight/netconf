/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.ErrorTag;

/**
 * Additional {@link ErrorTag}s.
 */
@NonNullByDefault
public final class ErrorTags {
    /**
     * Error reported when the request is valid, but the resource cannot be accessed. This tag typically maps to
     * {@link HttpStatusCode#SERVICE_UNAVAILABLE}.
     */
    // FIXME: redefine as SERVICE_UNAVAILABLE? It would be more obvious
    public static final ErrorTag RESOURCE_DENIED_TRANSPORT = new ErrorTag("resource-denied-transport");

    private ErrorTags() {
        // Hidden on purpose
    }
}
