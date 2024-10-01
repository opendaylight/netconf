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
 * A POST request to the /operations resource.
 */
@NonNullByDefault
final class PendingOperationsPost extends PendingRequest {
    private final MessageEncoding encoding;
    private final ApiPath apiPath;

    PendingOperationsPost(final MessageEncoding encoding, final ApiPath apiPath) {
        this.encoding = requireNonNull(encoding);
        this.apiPath = requireNonNull(apiPath);
    }

    // FIXME: stuff
    void execute(final RestconfServer server) {
//        server.operationsPOST(new NettyServerRequest<>(params, callback) {
//            @Override
//            FullHttpResponse transform(final InvokeResult result) {
//                final var output = result.output();
//                return output == null ? simpleResponse(requestParams, HttpResponseStatus.NO_CONTENT)
//                    : responseBuilder(requestParams, HttpResponseStatus.OK).setBody(output).build();
//            }
//        }, params.restconfURI(), apiPath,
//            requestBody(params, JsonOperationInputBody::new, XmlOperationInputBody::new));
    }
}
