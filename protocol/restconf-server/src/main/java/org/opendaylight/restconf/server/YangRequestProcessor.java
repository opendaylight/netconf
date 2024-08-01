/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.opendaylight.restconf.server.RequestUtils.extractApiPath;
import static org.opendaylight.restconf.server.RequestUtils.serverRequest;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static processor implementation serving {@code /yang-library-version} path request and
 * {@code /modules/{mountPath}/filename?revision}.
 */
final class YangRequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(YangRequestProcessor.class);

    private YangRequestProcessor() {
        // hidden on purpose
    }

    static void processYangLibraryVersion(final RequestParameters params, final RestconfServer service,
        final FutureCallback<FullHttpResponse> callback) {
        final var method = params.method();
        if (HttpMethod.GET.equals(method)) {
            LOG.debug("GET /yang-library-version");
            service.yangLibraryVersionGET(getRequest(params, callback));
        } else {
            LOG.debug("Unsupported method {}", method);
            callback.onSuccess(ResponseUtils.simpleErrorResponse(params, ErrorTag.OPERATION_NOT_SUPPORTED));
        }
    }

    static void processModules(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var method = params.method();
//        final var apiPath = extractApiPath(params);
//        final var pathParams = params.pathParameters();
        if (HttpMethod.GET.equals(method)) {
//            if (apiPath.isEmpty()) {
//                LOG.debug("GET /rests/modules/filename?revision");
//                service.modulesYangGET(getRequest(params, callback), pathParams.apiResource());
//            } else {
//                service.modulesYangGET(getRequest(params, callback), apiPath);
//            }
        } else {
            LOG.debug("Unsupported method {}", method);
            callback.onSuccess(ResponseUtils.simpleErrorResponse(params, ErrorTag.OPERATION_NOT_SUPPORTED));
        }
    }

    private static ServerRequest<FormattableBody> getRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        return serverRequest(params, callback, result ->
            responseBuilder(params, HttpResponseStatus.OK)
                .setBody(result)
                .build());
    }
}
