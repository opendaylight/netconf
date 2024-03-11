/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static org.opendaylight.restconf.server.impl.ResponseUtils.handleException;
import static org.opendaylight.restconf.server.impl.ResponseUtils.simpleResponse;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import java.util.Map;
import org.opendaylight.netconf.transport.http.RequestDispatcher;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStreamServletFactory;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RestconfRequestDispatcher implements RequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfRequestDispatcher.class);
    private static final Map<String, Processor> REQUEST_PROCESSORS = Map.of(
        PathParameters.DATA, DataRequestProcessor::process
        // TODO remaining processors
    );
    private static final Processor DEFAULT_PROCESSOR = (params, service, callback) ->
        callback.onSuccess(simpleResponse(params, HttpResponseStatus.NOT_FOUND));

    private final String basePath;
    private final RestconfServer restconfService;
    private final AsciiString defaultContentType;
    private final PrettyPrintParam defaultPrettyPrint;

    // TODO replace RestconfStreamServletFactory with dedicated configuration object
    public RestconfRequestDispatcher(@Reference final RestconfServer restconfService,
            @Reference final RestconfStreamServletFactory servletFactory) {
        this.restconfService = restconfService;
        basePath = "/" + servletFactory.restconf();
        defaultPrettyPrint = servletFactory.prettyPrint();
        defaultContentType = ContentTypes.APPLICATION_YANG_DATA_JSON;
        LOG.info("{} initialized with service {}", getClass().getSimpleName(), restconfService.getClass());
        LOG.info("Base path: {}, default content-type: {}, default pretty print: {}",
            basePath, defaultContentType, defaultPrettyPrint.value());
    }

    @Override
    @SuppressWarnings("IllegalCatch")
    public void dispatch(final FullHttpRequest request, final FutureCallback<FullHttpResponse> callback) {
        LOG.debug("Dispatching {} {}", request.method(), request.uri());
        final var params = new RequestParameters(basePath, request, defaultContentType, defaultPrettyPrint);
        try {
            REQUEST_PROCESSORS
                .getOrDefault(params.pathParameters().apiResource(), DEFAULT_PROCESSOR)
                .process(params, restconfService, callback);
        } catch (RuntimeException e) {
            handleException(params, callback, e);
        }
    }

    @FunctionalInterface
    interface Processor {
        void process(RequestParameters params, RestconfServer service, FutureCallback<FullHttpResponse> callback);
    }
}
