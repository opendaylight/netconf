/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.opendaylight.restconf.server.NettyMediaTypes.RESTCONF_TYPES;
import static org.opendaylight.restconf.server.RequestUtils.extractApiPath;
import static org.opendaylight.restconf.server.RequestUtils.requestBody;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonOperationInputBody;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.api.XmlOperationInputBody;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static processor implementation serving {@code /operations} path requests.
 */
final class OperationsRequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(OperationsRequestProcessor.class);

    private OperationsRequestProcessor() {
        // hidden on purpose
    }

    static void processOperationsRequest(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var method = params.method();
        final var contentType = params.contentType();
        final var apiPath = extractApiPath(params);

        if (HttpMethod.GET.equals(method)) {
            if (apiPath.isEmpty()) {
                LOG.debug("GET /operations");
                service.operationsGET(getRequest(params, callback));
            } else {
                LOG.debug("GET /operations/{}", apiPath);
                service.operationsGET(getRequest(params, callback), apiPath);
            }
        } else if (HttpMethod.POST.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            if (apiPath.isEmpty()) {
                LOG.debug("POST /operations is not allowed, we can not post operations to root");
                callback.onSuccess(ResponseUtils.simpleErrorResponse(params, ErrorTag.OPERATION_NOT_SUPPORTED));
            } else {
                LOG.debug("POST /operations/{}", apiPath);
                service.operationsPOST(postRequest(params, callback), URI.create(params.basePath()),
                    apiPath, operationInputBody(params));
            }
        } else {
            LOG.debug("Unsupported method {} or content type {}", method, contentType);
            callback.onSuccess(ResponseUtils.simpleErrorResponse(params, ErrorTag.OPERATION_NOT_SUPPORTED));
        }
    }

    private static OperationInputBody operationInputBody(final RequestParameters params) {
        return requestBody(params, JsonOperationInputBody::new, XmlOperationInputBody::new);
    }

    private static ServerRequest<FormattableBody> getRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        return new NettyServerRequest<>(params, callback, result ->
            responseBuilder(params, HttpResponseStatus.OK)
                .setBody(result)
                .build());
    }

    private static ServerRequest<InvokeResult> postRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        return new NettyServerRequest<>(params, callback, result ->
            responseBuilder(params, HttpResponseStatus.NO_CONTENT)
                .setBody(result.output())
                .build());
    }
}
