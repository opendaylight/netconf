/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.impl.ResponseUtils.handleException;
import static org.opendaylight.restconf.server.impl.ResponseUtils.simpleResponse;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import org.opendaylight.netconf.transport.http.RequestDispatcher;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RestconfRequestDispatcher implements RequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfRequestDispatcher.class);

    private final String basePath;
    private final RestconfServer restconfService;
    private final AsciiString defaultAcceptType;
    private final PrettyPrintParam defaultPrettyPrint;

    public RestconfRequestDispatcher(final RestconfServer restconfService, final String topLevelResource,
            final AsciiString defaultAcceptType, final PrettyPrintParam defaultPrettyPrint) {
        this.restconfService = requireNonNull(restconfService);
        basePath = "/" + requireNonNull(topLevelResource);
        this.defaultAcceptType = requireNonNull(defaultAcceptType);
        this.defaultPrettyPrint = requireNonNull(defaultPrettyPrint);
        LOG.info("{} initialized with service {}", getClass().getSimpleName(), restconfService.getClass());
        LOG.info("Base path: {}, default accept: {}, default pretty print: {}",
            basePath, defaultAcceptType, defaultPrettyPrint.value());
    }

    @Override
    @SuppressWarnings("IllegalCatch")
    public void dispatch(final FullHttpRequest request, final FutureCallback<FullHttpResponse> callback) {
        LOG.debug("Dispatching {} {}", request.method(), request.uri());

        try {
            final var params = new RequestParameters(basePath, request, defaultAcceptType, defaultPrettyPrint);
            final var apiResource = params.pathParameters().apiResource();
            if (PathParameters.DATA.equals(apiResource)) {
                DataRequestProcessor.processDataRequest(params, restconfService, callback);
            } else if (PathParameters.OPERATIONS.equals(apiResource)) {
                // TODO implement
                callback.onSuccess(simpleResponse(params, HttpResponseStatus.NOT_IMPLEMENTED));
            } else if (PathParameters.YANG_LIBRARY_VERSION.equals(apiResource)) {
                // TODO implement
                callback.onSuccess(simpleResponse(params, HttpResponseStatus.NOT_IMPLEMENTED));
            } else if (PathParameters.MODULES.equals(apiResource)) {
                // TODO implement
                callback.onSuccess(simpleResponse(params, HttpResponseStatus.NOT_IMPLEMENTED));
            } else if (PathParameters.HOST_META.equals(apiResource)) {
                // TODO implement
                callback.onSuccess(simpleResponse(params, HttpResponseStatus.NOT_IMPLEMENTED));
            } else if (PathParameters.HOST_META_JSON.equals(apiResource)) {
                // TODO implement
                callback.onSuccess(simpleResponse(params, HttpResponseStatus.NOT_IMPLEMENTED));
            } else {
                callback.onSuccess(simpleResponse(params, HttpResponseStatus.NOT_FOUND));
            }
        } catch (RuntimeException e) {
            handleException(request, callback, e);
        }
    }
}
