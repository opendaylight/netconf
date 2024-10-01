/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;
import static org.opendaylight.restconf.server.ResponseUtils.simpleResponse;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;

/**
 * A POST request to the /data resource.
 */
@NonNullByDefault
final class PendingDataPost extends PendingRequest {
    private final MessageEncoding encoding;
    private final ApiPath apiPath;

    PendingDataPost(final MessageEncoding encoding, final ApiPath apiPath) {
        this.encoding = requireNonNull(encoding);
        this.apiPath = requireNonNull(apiPath);
    }

    // FIXME: stuff
    void execute(final RestconfServer server) {
//        if (apiPath.isEmpty()) {
//            server.dataPOST(postRequest(params, callback),
//                requestBody(params, JsonChildBody::new, XmlChildBody::new));
//        } else {
//            server.dataPOST(postRequest(params, callback), apiPath,
//                requestBody(params, JsonDataPostBody::new, XmlDataPostBody::new));
//        }
    }

    private static <T extends DataPostResult> ServerRequest<T> postRequest(final RequestParameters params,
            final RestconfRequest callback) {
        return new NettyServerRequest<>(params, callback) {
            @Override
            FullHttpResponse transform(final DataPostResult result) {
                return switch (result) {
                    case CreateResourceResult createResult -> {
                        yield responseBuilder(requestParams, HttpResponseStatus.CREATED)
                        .setHeader(HttpHeaderNames.LOCATION,
                            requestParams.restconfURI() + "data/" + createResult.createdPath())
                        .setMetadataHeaders(createResult)
                        .build();
                    }
                    case InvokeResult invokeResult -> {
                        final var output = invokeResult.output();
                        yield output == null ? simpleResponse(requestParams, HttpResponseStatus.NO_CONTENT)
                            : responseBuilder(requestParams, HttpResponseStatus.OK).setBody(output).build();
                    }
                };
            }
        };
    }
}
