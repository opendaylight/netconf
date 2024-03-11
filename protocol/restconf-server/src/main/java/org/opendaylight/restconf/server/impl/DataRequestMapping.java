/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static org.opendaylight.restconf.server.impl.ContentTypes.RESTCONF_TYPES;
import static org.opendaylight.restconf.server.impl.ContentTypes.YANG_PATCH_TYPES;
import static org.opendaylight.restconf.server.impl.PathParameters.DATA;
import static org.opendaylight.restconf.server.impl.RequestUtils.buildServerRequest;
import static org.opendaylight.restconf.server.impl.RequestUtils.childBody;
import static org.opendaylight.restconf.server.impl.RequestUtils.dataPostBody;
import static org.opendaylight.restconf.server.impl.RequestUtils.extractApiPath;
import static org.opendaylight.restconf.server.impl.ResponseUtils.callback;
import static org.opendaylight.restconf.server.impl.ResponseUtils.setResponse;
import static org.opendaylight.restconf.server.impl.ResponseUtils.setStatusOnlyResponse;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.List;
import java.util.Map;
import org.opendaylight.restconf.common.errors.RestconfCallback;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static collection of {@link RequestProcessor} instances serving {@code /data} path requests.
 */
final class DataRequestMapping {
    private static final Logger LOG = LoggerFactory.getLogger(DataRequestMapping.class);

    static final List<RequestProcessor> PROCESSORS = List.of(

        // GET /data(/.+)?
        new RequestProcessor(DATA, HttpMethod.GET,
            (service, params) -> {
                final var apiPath = extractApiPath(params);
                // FIXME: inline call combining the callback
                final var serverRequest = buildServerRequest(params);
                final var future = apiPath.isEmpty()
                    ? service.dataGET(serverRequest) : service.dataGET(serverRequest, apiPath);
                future.addCallback(callback(params, result ->
                    setResponse(params, result, DataGetResult::body, serverRequest.prettyPrint(),
                        Map.of(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE))));
            }),

        // POST /data(/.+)?
        new RequestProcessor(DATA, HttpMethod.POST, RESTCONF_TYPES,
            (service, params) -> {
                final var apiPath = extractApiPath(params);
                final var serverRequest = buildServerRequest(params);
                final RestconfCallback<DataPostResult> callback = callback(params, result -> {
                    if (result instanceof CreateResourceResult createResult) {
                        final var location = params.basePath() + DATA + "/" + createResult.createdPath();
                        setResponse(params, createResult, HttpResponseStatus.CREATED,
                            Map.of(HttpHeaderNames.LOCATION, location));

                    } else if (result instanceof InvokeResult invokeResult) {
                        setResponse(params, invokeResult, InvokeResult::output, serverRequest.prettyPrint());

                    } else {
                        LOG.error("Unhandled result {}", result);
                        setStatusOnlyResponse(params, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    }
                });
                if (apiPath.isEmpty()) {
                    service.dataPOST(serverRequest, childBody(params)).addCallback(callback);
                } else {
                    service.dataPOST(serverRequest, apiPath, dataPostBody(params)).addCallback(callback);
                }
            }),

        // PUT /data(/.*)?
        new RequestProcessor(DATA, HttpMethod.PUT, RESTCONF_TYPES, (service, params) -> {
            // FIXME implement
            setStatusOnlyResponse(params, HttpResponseStatus.NOT_IMPLEMENTED);
        }),

        // PATCH /data(/.*)?
        new RequestProcessor(DATA, HttpMethod.PATCH, RESTCONF_TYPES, (service, params) -> {
            // FIXME implement
            setStatusOnlyResponse(params, HttpResponseStatus.NOT_IMPLEMENTED);
        }),

        // DELETE /data/.*
        new RequestProcessor(DATA, HttpMethod.DELETE, (service, params) -> {
            // FIXME implement
            setStatusOnlyResponse(params, HttpResponseStatus.NOT_IMPLEMENTED);
        }),

        // PATCH /data (yang-patch case)
        new RequestProcessor(DATA, HttpMethod.PATCH, YANG_PATCH_TYPES, (service, params) -> {
            // FIXME implement
            setStatusOnlyResponse(params, HttpResponseStatus.NOT_IMPLEMENTED);
        })
    );

    private DataRequestMapping() {
        // hidden on purpose
    }
}
