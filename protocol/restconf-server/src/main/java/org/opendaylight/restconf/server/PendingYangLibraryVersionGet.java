/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.RestconfServer;

/**
 * A GET or HEAD request to the /yang-library-version resource.
 */
@NonNullByDefault
final class PendingYangLibraryVersionGet extends PendingRequest {
    private final MessageEncoding encoding;
    private final boolean withContent;

    PendingYangLibraryVersionGet(final MessageEncoding encoding, final boolean withContent) {
        this.encoding = requireNonNull(encoding);
        this.withContent = withContent;
    }

    // FIXME: stuff
    void execute(final RestconfServer server) {
//        server.yangLibraryVersionGET(new FormattableServerRequest(params, callback));
    }
}
