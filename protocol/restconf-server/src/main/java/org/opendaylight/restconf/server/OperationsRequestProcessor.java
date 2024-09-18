/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.opendaylight.restconf.server.RequestUtils.extractApiPath;
import static org.opendaylight.restconf.server.RequestUtils.requestBody;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;
import static org.opendaylight.restconf.server.ResponseUtils.simpleResponse;
import static org.opendaylight.restconf.server.ResponseUtils.unmappedRequestErrorResponse;
import static org.opendaylight.restconf.server.ResponseUtils.unsupportedMediaTypeErrorResponse;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonOperationInputBody;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.XmlOperationInputBody;

/**
 * Static request processor serving operation resource requests.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-3.3.2">RFC 8040.
 * Section 3.3.2. {+restconf}/operations</a>
 */
final class OperationsRequestProcessor {
    private OperationsRequestProcessor() {
        // hidden on purpose
    }

    static void processOperationsRequest(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var apiPath = extractApiPath(params);
        switch (params.method().name()) {
            case "OPTIONS" -> {
                if (apiPath.isEmpty()) {
                    callback.onSuccess(OptionsServerRequest.withoutPatch(params.protocolVersion(),
                        "GET, HEAD, OPTIONS"));
                } else {
                    service.operationsOPTIONS(new OptionsServerRequest(params, callback), apiPath);
                }
            }
            case "HEAD", "GET" -> getOperations(params, service, callback, apiPath);
            case "POST" -> {
                if (NettyMediaTypes.RESTCONF_TYPES.contains(params.contentType())) {
                    // invoke rpc -> https://datatracker.ietf.org/doc/html/rfc8040#section-4.4.2
                    postOperations(params, service, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    private static void getOperations(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<FormattableBody>(params, callback) {
            @Override
            FullHttpResponse transform(final FormattableBody result) {
                return responseBuilder(params, HttpResponseStatus.OK).setBody(result).build();
            }
        };
        if (apiPath.isEmpty()) {
            service.operationsGET(request);
        } else {
            service.operationsGET(request, apiPath);
        }
    }

    private static void postOperations(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        service.operationsPOST(new NettyServerRequest<>(params, callback) {
            @Override
            FullHttpResponse transform(final InvokeResult result) {
                final var output = result.output();
                return output == null ? simpleResponse(params, HttpResponseStatus.NO_CONTENT)
                    : responseBuilder(params, HttpResponseStatus.OK).setBody(output).build();
            }
        }, restconfUri(params.baseUri()), apiPath,
            requestBody(params, JsonOperationInputBody::new, XmlOperationInputBody::new));
    }

    private static URI restconfUri(final URI uri) {
        // we need to add `/` at the end of RESTCONF base path to create correct streams URLs
        return URI.create(uri.toString().concat("/"));
    }
}
