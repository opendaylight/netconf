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
import static org.opendaylight.restconf.server.ResponseUtils.simpleResponse;
import static org.opendaylight.restconf.server.ResponseUtils.unmappedRequestErrorResponse;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonOperationInputBody;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.XmlOperationInputBody;

/**
 * Static processor implementation serving {@code /operations} path requests.
 */
final class OperationsRequestProcessor {

    private OperationsRequestProcessor() {
        // hidden on purpose
    }

    static void processOperationsRequest(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var method = params.method();
        final var contentType = params.contentType();
        final var apiPath = extractApiPath(params);

        if (HttpMethod.GET.equals(method)) {
            getOperations(params, service, callback, apiPath);
        } else if (HttpMethod.POST.equals(method) && !apiPath.isEmpty() && RESTCONF_TYPES.contains(contentType)) {
            postOperations(params, service, callback, apiPath);
        } else {
            callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    private static void getOperations(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<FormattableBody>(params, callback,
            result -> responseBuilder(params, HttpResponseStatus.OK).setBody(result).build());
        if (apiPath.isEmpty()) {
            service.operationsGET(request);
        } else {
            service.operationsGET(request, apiPath);
        }
    }

    private static void postOperations(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<InvokeResult>(params, callback,
            result -> {
                final var output = result.output();
                return output == null ? simpleResponse(params, HttpResponseStatus.NO_CONTENT)
                    : responseBuilder(params, HttpResponseStatus.OK).setBody(output).build();
            });
        final var operationInputBody = requestBody(params, JsonOperationInputBody::new, XmlOperationInputBody::new);
        service.operationsPOST(request, URI.create(params.basePath()), apiPath, operationInputBody);
    }
}
