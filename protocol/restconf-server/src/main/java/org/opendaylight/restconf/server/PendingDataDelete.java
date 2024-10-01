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
 * A DELETE request to the /data resource.
 */
@NonNullByDefault
final class PendingDataDelete extends PendingRequest {
    private final ApiPath apiPath;

    PendingDataDelete(final ApiPath apiPath) {
        this.apiPath = requireNonNull(apiPath);
    }

    // FIXME: stuff
    void execute(final RestconfServer server) {
//        server.dataDELETE(new NettyServerRequest<>(params, callback) {
//            @Override
//            FullHttpResponse transform(final Empty result) {
//                return simpleResponse(requestParams, HttpResponseStatus.NO_CONTENT);
//            }
//        }, apiPath);
    }
}
