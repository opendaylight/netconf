/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.opendaylight.restconf.server.NettyMediaTypes.RESTCONF_TYPES;
import static org.opendaylight.restconf.server.NettyMediaTypes.YANG_PATCH_TYPES;
import static org.opendaylight.restconf.server.RequestUtils.extractApiPath;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static processor implementation serving {@code /data} path requests.
 */
final class DataRequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(DataRequestProcessor.class);

    private DataRequestProcessor() {
        // hidden on purpose
    }

    static void processDataRequest(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var method = params.method();
        final var contentType = params.contentType();
        final var apiPath = extractApiPath(params);

        if (HttpMethod.GET.equals(method)) {
            // GET /data(/.+)?
            if (apiPath.isEmpty()) {
                service.dataGET(getRequest(params, callback));
            } else {
                service.dataGET(getRequest(params, callback), apiPath);
            }
        } else if (HttpMethod.POST.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            // POST /data(/.+)?
            if (apiPath.isEmpty()) {
                // service.dataPOST(postRequest(params, callback), childBody(params));
            } else {
                // service.dataPOST(postRequest(params, callback), apiPath, dataPostBody(params));
            }
        } else if (HttpMethod.PUT.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            // PUT /data(/.+)?
            // FIXME implement
        } else if (HttpMethod.PATCH.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            // PATCH /data(/.*)? RESTCONF patch case
            // FIXME implement
        } else if (HttpMethod.PATCH.equals(method) && YANG_PATCH_TYPES.contains(contentType)) {
            // PATCH /data (yang-patch case)
            // FIXME implement
        } else if (HttpMethod.DELETE.equals(method)) {
            // DELETE /data/.*
            // FIXME implement
        } else {
            // FIXME implement
        }
    }

    private static NettyServerRequest<DataGetResult> getRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        return new NettyServerRequest<>(params, callback, result ->
            responseBuilder(params, HttpResponseStatus.OK)
                .setHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
                .setMetadataHeaders(result).setBody(result.body()).build()) {
            @Override
            FullHttpResponse transform(final DataGetResult result) throws ServerException {
                return responseBuilder(params, HttpResponseStatus.OK)
                    .setHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
                    .setMetadataHeaders(result)
                    .setBody(result.body())
                    .build();
            }
        };
    }
}
