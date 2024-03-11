/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.ResponseUtils.handleException;
import static org.opendaylight.restconf.server.ResponseUtils.simpleErrorResponse;

import com.google.common.util.concurrent.FutureCallback;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.AsciiString;
import org.opendaylight.netconf.transport.http.RequestDispatcher;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RestconfRequestDispatcher implements RequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfRequestDispatcher.class);

    private final String basePath;
    private final RestconfServer restconfService;
    private final PrincipalService principalService;
    private final ErrorTagMapping errorTagMapping;
    private final AsciiString defaultAcceptType;
    private final PrettyPrintParam defaultPrettyPrint;

    public RestconfRequestDispatcher(final RestconfServer restconfService, final PrincipalService principalService,
            final String topLevelResource, final ErrorTagMapping errorTagMapping,
            final AsciiString defaultAcceptType, final PrettyPrintParam defaultPrettyPrint) {
        this.restconfService = requireNonNull(restconfService);
        this.principalService = requireNonNull(principalService);
        basePath = "/" + requireNonNull(topLevelResource);
        this.errorTagMapping = requireNonNull(errorTagMapping);
        this.defaultAcceptType = requireNonNull(defaultAcceptType);
        this.defaultPrettyPrint = requireNonNull(defaultPrettyPrint);

        LOG.info("{} initialized with service {}", getClass().getSimpleName(), restconfService.getClass());
        LOG.info("Base path: {}, default accept: {}, default pretty print: {}",
            basePath, defaultAcceptType, defaultPrettyPrint.value());
    }

    @Override
    @SuppressWarnings("IllegalCatch")
    @SuppressFBWarnings("DB_DUPLICATE_SWITCH_CLAUSES")
    public void dispatch(final FullHttpRequest request, final FutureCallback<FullHttpResponse> callback) {
        LOG.debug("Dispatching {} {}", request.method(), request.uri());

        try {
            final var principal = principalService.acquirePrincipal(request);
            final var params = new RequestParameters(basePath, request, principal,
                errorTagMapping, defaultAcceptType, defaultPrettyPrint);
            switch (params.pathParameters().apiResource()) {
                case PathParameters.DATA -> DataRequestProcessor.processDataRequest(params, restconfService, callback);
                case PathParameters.OPERATIONS -> {
                    // TODO implement
                }
                case PathParameters.YANG_LIBRARY_VERSION -> {
                    // TODO implement
                }
                case PathParameters.MODULES -> {
                    // TODO implement
                }
                case PathParameters.HOST_META -> {
                    // TODO implement
                }
                case PathParameters.HOST_META_JSON -> {
                }
                default -> callback.onSuccess(simpleErrorResponse(params, ErrorTag.DATA_MISSING));
            }
        } catch (RuntimeException e) {
            handleException(request, errorTagMapping, callback, e);
        }
    }
}
