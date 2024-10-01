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
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.RestconfServer;

/**
 * A GET or HEAD request to the /data resource.
 */
@NonNullByDefault
final class DataGetRequest extends RequestHandle {
    private final MessageEncoding encoding;
    private final ApiPath apiPath;
    private final boolean withContent;

    DataGetRequest(final MessageEncoding encoding, final ApiPath apiPath, final boolean withContent) {
        this.encoding = requireNonNull(encoding);
        this.apiPath = requireNonNull(apiPath);
        this.withContent = withContent;
    }

    // FIXME: stuff
    void execute(final RestconfServer server) {
        if (apiPath.isEmpty()) {
            server.dataGET(null);
        } else {
            server.dataGET(null, apiPath);
        }
    }
}
