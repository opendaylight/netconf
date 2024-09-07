/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.ResponseUtils.optionsResponse;
import static org.opendaylight.restconf.server.ResponseUtils.simpleErrorResponse;
import static org.opendaylight.restconf.server.ResponseUtils.unmappedRequestErrorResponse;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.net.URI;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RestconfRequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfRequestDispatcher.class);

    private final URI baseUri;
    private final RestconfServer restconfService;
    private final PrincipalService principalService;
    private final ErrorTagMapping errorTagMapping;
    private final AsciiString defaultAcceptType;
    private final PrettyPrintParam defaultPrettyPrint;

    RestconfRequestDispatcher(final RestconfServer restconfService, final PrincipalService principalService,
            final URI baseUri, final ErrorTagMapping errorTagMapping,
            final AsciiString defaultAcceptType, final PrettyPrintParam defaultPrettyPrint) {
        this.restconfService = requireNonNull(restconfService);
        this.principalService = requireNonNull(principalService);
        this.baseUri = requireNonNull(baseUri);
        this.errorTagMapping = requireNonNull(errorTagMapping);
        this.defaultAcceptType = requireNonNull(defaultAcceptType);
        this.defaultPrettyPrint = requireNonNull(defaultPrettyPrint);

        LOG.info("{} initialized with service {}", getClass().getSimpleName(), restconfService.getClass());
        LOG.info("Base path: {}, default accept: {}, default pretty print: {}",
            baseUri, defaultAcceptType, defaultPrettyPrint.value());
    }

    @SuppressWarnings("IllegalCatch")
    void dispatch(final QueryStringDecoder decoder, final FullHttpRequest request,
            final FutureCallback<FullHttpResponse> callback) {
        LOG.debug("Dispatching {} {}", request.method(), request.uri());

        final var principal = principalService.acquirePrincipal(request);
        final var params = new RequestParameters(baseUri, decoder, request, principal,
            errorTagMapping, defaultAcceptType, defaultPrettyPrint);
        try {
            switch (params.pathParameters().apiResource()) {
                case PathParameters.DATA -> DataRequestProcessor.processDataRequest(params, restconfService, callback);
                case PathParameters.OPERATIONS ->
                    OperationsRequestProcessor.processOperationsRequest(params, restconfService, callback);
                case PathParameters.YANG_LIBRARY_VERSION ->
                    ModulesRequestProcessor.processYangLibraryVersion(params, restconfService, callback);
                case PathParameters.MODULES ->
                    ModulesRequestProcessor.processModules(params, restconfService, callback);
                default -> callback.onSuccess(
                    params.method() == Method.OPTIONS
                        ? optionsResponse(params, Method.OPTIONS.name())
                        : unmappedRequestErrorResponse(params));
            }
        } catch (RuntimeException e) {
            LOG.error("Error processing request {} {}", request.method(), request.uri(), e);
            final var errorTag = e instanceof ServerErrorException see ? see.errorTag() : ErrorTag.OPERATION_FAILED;
            callback.onSuccess(simpleErrorResponse(params, errorTag, e.getMessage()));
        }
    }
}
