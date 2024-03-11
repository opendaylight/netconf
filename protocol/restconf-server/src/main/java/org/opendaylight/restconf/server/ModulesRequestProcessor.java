/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.opendaylight.restconf.server.Method.GET;
import static org.opendaylight.restconf.server.Method.HEAD;
import static org.opendaylight.restconf.server.Method.OPTIONS;
import static org.opendaylight.restconf.server.ResponseUtils.allowHeaderValue;
import static org.opendaylight.restconf.server.ResponseUtils.optionsResponse;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;
import static org.opendaylight.restconf.server.ResponseUtils.simpleErrorResponse;
import static org.opendaylight.restconf.server.ResponseUtils.simpleResponse;
import static org.opendaylight.restconf.server.ResponseUtils.unmappedRequestErrorResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.ErrorTag;

/**
 * Static processor implementation serving {@code /yang-library-version} and {@code /modules} path requests.
 */
final class ModulesRequestProcessor {
    private static final String ALLOW_METHODS = allowHeaderValue(OPTIONS, HEAD, GET);

    @VisibleForTesting
    static final String REVISION = "revision";
    @VisibleForTesting
    static final String MISSING_FILENAME_ERROR = "Module name is missing";
    @VisibleForTesting
    static final String SOURCE_READ_FAILURE_ERROR = "Failure reading module source: ";

    private ModulesRequestProcessor() {
        // hidden on purpose
    }

    static void processYangLibraryVersion(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        switch (params.method()) {
            case OPTIONS -> callback.onSuccess(optionsResponse(params, ALLOW_METHODS));
            case HEAD, GET -> getYangLibraryVersion(params, service, callback);
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    static void processModules(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        switch (params.method()) {
            case OPTIONS -> callback.onSuccess(optionsResponse(params, ALLOW_METHODS));
            case HEAD, GET ->  getModule(params, service, callback);
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    private static void getYangLibraryVersion(final RequestParameters params, final RestconfServer service,
        final FutureCallback<FullHttpResponse> callback) {
        final var request = new NettyServerRequest<FormattableBody>(params, callback,
            result -> responseBuilder(params, HttpResponseStatus.OK).setBody(result).build());
        service.yangLibraryVersionGET(request);
    }

    private static void getModule(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var file = extractModuleFile(params.pathParameters().childIdentifier());
        final var revision = params.queryParameters().lookup(REVISION);
        if (file.name().isEmpty()) {
            callback.onSuccess(simpleErrorResponse(params, ErrorTag.MISSING_ELEMENT, MISSING_FILENAME_ERROR));
        }
        final var acceptYang = params.requestHeaders()
            .contains(HttpHeaderNames.ACCEPT, NettyMediaTypes.APPLICATION_YANG, true);
        final var acceptYin = params.requestHeaders()
            .contains(HttpHeaderNames.ACCEPT, NettyMediaTypes.APPLICATION_YIN_XML, true);
        if (acceptYin && !acceptYang) {
            // YIN if explicitly requested
            final var request = getModuleRequest(params, callback,NettyMediaTypes.APPLICATION_YIN_XML);
            if (file.mountPath.isEmpty()) {
                service.modulesYinGET(request, file.name(), revision);
            } else {
                service.modulesYinGET(request, file.mountPath(), file.name(), revision);
            }
        } else {
            // YANG by default, incl accept any
            final var request = getModuleRequest(params, callback,NettyMediaTypes.APPLICATION_YANG);
            if (file.mountPath.isEmpty()) {
                service.modulesYangGET(request, file.name(), revision);
            } else {
                service.modulesYangGET(request, file.mountPath(), file.name(), revision);
            }
        }
    }

    private static ServerRequest<ModulesGetResult> getModuleRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback, final AsciiString mediaType) {
        return new NettyServerRequest<>(params, callback,
            result -> {
                final byte[] bytes;
                try {
                    bytes = result.source().asByteSource(StandardCharsets.UTF_8).read();
                } catch (IOException e) {
                    throw new ServerErrorException(ErrorTag.OPERATION_FAILED,
                       SOURCE_READ_FAILURE_ERROR + e.getMessage(), e);
                }
                return simpleResponse(params, HttpResponseStatus.OK, mediaType, bytes);
            });
    }

    private static ModuleFile extractModuleFile(final String path) {
        // optional mountPath followed by file name separated by slash
        final var lastIndex = path.length() - 1;
        final var splitIndex = path.lastIndexOf('/');
        if (splitIndex < 0) {
            return new ModuleFile(ApiPath.empty(), path);
        }
        final var apiPath = RequestUtils.extractApiPath(path.substring(0, splitIndex));
        final var name = splitIndex == lastIndex ? "" : path.substring(splitIndex + 1);
        return new ModuleFile(apiPath, name);
    }

    private record ModuleFile(ApiPath mountPath, String name) {
    }
}
