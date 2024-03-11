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
import static org.opendaylight.restconf.server.RequestUtils.requestBody;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;
import static org.opendaylight.restconf.server.ResponseUtils.simpleResponse;

import com.google.common.util.concurrent.FutureCallback;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.opendaylight.restconf.server.api.ChildBody;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPostBody;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonChildBody;
import org.opendaylight.restconf.server.api.JsonDataPostBody;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.api.XmlChildBody;
import org.opendaylight.restconf.server.api.XmlDataPostBody;
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

    @SuppressFBWarnings("UCF_USELESS_CONTROL_FLOW")
    static void processDataRequest(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var method = params.method();
        final var contentType = params.contentType();

        if (HttpMethod.GET.equals(method)) {
            dataGET(params, service, callback);
        } else if (HttpMethod.POST.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            dataPOST(params, service, callback);
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

    private static void dataGET(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var request = new NettyServerRequest<DataGetResult>(params, callback,
            result -> responseBuilder(params, HttpResponseStatus.OK)
                .setHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
                .setMetadataHeaders(result).setBody(result.body()).build());
        final var apiPath = extractApiPath(params);
        if (apiPath.isEmpty()) {
            service.dataGET(request);
        } else {
            service.dataGET(request, apiPath);
        }
    }

    private static void dataPOST(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var apiPath = extractApiPath(params);
        if (apiPath.isEmpty()) {
            final ChildBody childBody = requestBody(params, JsonChildBody::new, XmlChildBody::new);
            service.dataPOST(postRequest(params, callback), childBody);
        } else {
            final DataPostBody dataPostBody = requestBody(params, JsonDataPostBody::new, XmlDataPostBody::new);
            service.dataPOST(postRequest(params, callback), apiPath, dataPostBody);
        }
    }

    private static <T extends DataPostResult> ServerRequest<T> postRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        return new NettyServerRequest<>(params, callback, result -> {
            if (result instanceof CreateResourceResult createResult) {
                final var location = params.basePath() + PathParameters.DATA + "/" + createResult.createdPath();
                return responseBuilder(params, HttpResponseStatus.CREATED)
                    .setHeader(HttpHeaderNames.LOCATION, location)
                    .setMetadataHeaders(createResult).build();
            }
            if (result instanceof InvokeResult invokeResult) {
                final var output = invokeResult.output();
                return output == null ? simpleResponse(params, HttpResponseStatus.NO_CONTENT)
                    : responseBuilder(params, HttpResponseStatus.OK).setBody(output).build();
            }
            LOG.error("Unhandled result {}", result);
            return simpleResponse(params, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        });
    }
}
