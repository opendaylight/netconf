/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.NettyMediaTypes.RESTCONF_TYPES;
import static org.opendaylight.restconf.server.NettyMediaTypes.YANG_PATCH_TYPES;
import static org.opendaylight.restconf.server.RequestUtils.extractApiPath;
import static org.opendaylight.restconf.server.RequestUtils.requestBody;
import static org.opendaylight.restconf.server.ResponseUtils.optionsResponse;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;
import static org.opendaylight.restconf.server.ResponseUtils.responseStatus;
import static org.opendaylight.restconf.server.ResponseUtils.simpleErrorResponse;
import static org.opendaylight.restconf.server.ResponseUtils.simpleResponse;
import static org.opendaylight.restconf.server.ResponseUtils.unmappedRequestErrorResponse;
import static org.opendaylight.restconf.server.ResponseUtils.unsupportedMediaTypeErrorResponse;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.net.URI;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonChildBody;
import org.opendaylight.restconf.server.api.JsonDataPostBody;
import org.opendaylight.restconf.server.api.JsonPatchBody;
import org.opendaylight.restconf.server.api.JsonResourceBody;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.api.XmlChildBody;
import org.opendaylight.restconf.server.api.XmlDataPostBody;
import org.opendaylight.restconf.server.api.XmlPatchBody;
import org.opendaylight.restconf.server.api.XmlResourceBody;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.YangPatchStatusBody;
import org.opendaylight.yangtools.yang.common.Empty;
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
                case PathParameters.DATA -> processDataRequest(params, restconfService, callback);
                case PathParameters.OPERATIONS ->
                    OperationsRequestProcessor.processOperationsRequest(params, restconfService, callback);
                case PathParameters.YANG_LIBRARY_VERSION ->
                    ModulesRequestProcessor.processYangLibraryVersion(params, restconfService, callback);
                case PathParameters.MODULES ->
                    ModulesRequestProcessor.processModules(params, restconfService, callback);
                default -> callback.onSuccess(
                    HttpMethod.OPTIONS.equals(params.method())
                        ? optionsResponse(params, HttpMethod.OPTIONS.name())
                        : unmappedRequestErrorResponse(params));
            }
        } catch (RuntimeException e) {
            LOG.error("Error processing request {} {}", request.method(), request.uri(), e);
            final var errorTag = e instanceof ServerErrorException see ? see.errorTag() : ErrorTag.OPERATION_FAILED;
            callback.onSuccess(simpleErrorResponse(params, errorTag, e.getMessage()));
        }
    }

    /**
     * Process a request to <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.1">RFC 8040 {+restconf}/data</a>
     * resource.
     */
    private static void processDataRequest(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var contentType = params.contentType();
        final var apiPath = extractApiPath(params);
        switch (params.method().name()) {
            // resource options -> https://www.rfc-editor.org/rfc/rfc8040#section-4.1
            case "OPTIONS" -> {
                final var request = new OptionsServerRequest(params, callback);
                if (apiPath.isEmpty()) {
                    service.dataOPTIONS(request);
                } else {
                    service.dataOPTIONS(request, apiPath);
                }
            }
            // retrieve data and metadata for a resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.3
            // HEAD is same as GET but without content -> https://www.rfc-editor.org/rfc/rfc8040#section-4.2
            case "HEAD", "GET" -> getData(params, service, callback, apiPath);
            case "POST" -> {
                if (RESTCONF_TYPES.contains(contentType)) {
                    // create resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1
                    // or invoke an action -> https://www.rfc-editor.org/rfc/rfc8040#section-3.6
                    postData(params, service, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            case "PUT" -> {
                if (RESTCONF_TYPES.contains(contentType)) {
                    // create or replace target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.5
                    putData(params, service, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            case "PATCH" -> {
                if (RESTCONF_TYPES.contains(contentType)) {
                    // Plain RESTCONF patch = merge target resource content ->
                    // https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1
                    patchData(params, service, callback, apiPath);
                } else if (YANG_PATCH_TYPES.contains(contentType)) {
                    // YANG Patch = ordered list of edits that are applied to the target datastore ->
                    // https://www.rfc-editor.org/rfc/rfc8072#section-2
                    yangPatchData(params, service, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            // delete target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.7
            case "DELETE" -> deleteData(params, service, callback, apiPath);
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    private static void getData(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataGetResult>(params, callback) {
            @Override
            FullHttpResponse transform(final DataGetResult result) {
                return responseBuilder(params, HttpResponseStatus.OK)
                    .setHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
                    .setMetadataHeaders(result).setBody(result.body()).build();
            }
        };

        if (apiPath.isEmpty()) {
            service.dataGET(request);
        } else {
            service.dataGET(request, apiPath);
        }
    }

    private static void postData(final RequestParameters params, final RestconfServer service,
        final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        if (apiPath.isEmpty()) {
            service.dataPOST(postRequest(params, callback), requestBody(params, JsonChildBody::new, XmlChildBody::new));
        } else {
            service.dataPOST(postRequest(params, callback), apiPath,
                requestBody(params, JsonDataPostBody::new, XmlDataPostBody::new));
        }
    }

    private static <T extends DataPostResult> ServerRequest<T> postRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        return new NettyServerRequest<>(params, callback) {
            @Override
            FullHttpResponse transform(final DataPostResult result) {
                return switch (result) {
                    case CreateResourceResult createResult -> {
                        yield responseBuilder(params, HttpResponseStatus.CREATED)
                        .setHeader(HttpHeaderNames.LOCATION,
                            params.baseUri() + PathParameters.DATA + "/" + createResult.createdPath())
                        .setMetadataHeaders(createResult)
                        .build();
                    }
                    case InvokeResult invokeResult -> {
                        final var output = invokeResult.output();
                        yield output == null ? simpleResponse(params, HttpResponseStatus.NO_CONTENT)
                            : responseBuilder(params, HttpResponseStatus.OK).setBody(output).build();
                    }
                };
            }
        };
    }

    private static void putData(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataPutResult>(params, callback) {
            @Override
            FullHttpResponse transform(final DataPutResult result) {
                final var status = result.created() ? HttpResponseStatus.CREATED : HttpResponseStatus.NO_CONTENT;
                return responseBuilder(params, status).setMetadataHeaders(result).build();
            }
        };
        final var dataResourceBody = requestBody(params, JsonResourceBody::new, XmlResourceBody::new);
        if (apiPath.isEmpty()) {
            service.dataPUT(request, dataResourceBody);
        } else {
            service.dataPUT(request, apiPath, dataResourceBody);
        }
    }

    private static void patchData(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataPatchResult>(params, callback) {
            @Override
            FullHttpResponse transform(final DataPatchResult result) {
                return responseBuilder(params, HttpResponseStatus.OK).setMetadataHeaders(result).build();
            }
        };
        final var dataResourceBody = requestBody(params, JsonResourceBody::new, XmlResourceBody::new);
        if (apiPath.isEmpty()) {
            service.dataPATCH(request, dataResourceBody);
        } else {
            service.dataPATCH(request, apiPath, dataResourceBody);
        }
    }

    private static void yangPatchData(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataYangPatchResult>(params, callback) {
            @Override
            FullHttpResponse transform(final DataYangPatchResult result) {
                final var patchStatus = result.status();
                return responseBuilder(params, patchResponseStatus(patchStatus, params.errorTagMapping()))
                    .setBody(new YangPatchStatusBody(patchStatus))
                    .setMetadataHeaders(result)
                    .build();
            }
        };
        final var yangPatchBody = requestBody(params, JsonPatchBody::new, XmlPatchBody::new);
        if (apiPath.isEmpty()) {
            service.dataPATCH(request, yangPatchBody);
        } else {
            service.dataPATCH(request, apiPath, yangPatchBody);
        }
    }

    private static HttpResponseStatus patchResponseStatus(final PatchStatusContext statusContext,
        final ErrorTagMapping errorTagMapping) {
        if (statusContext.ok()) {
            return HttpResponseStatus.OK;
        }
        final var globalErrors = statusContext.globalErrors();
        if (globalErrors != null && !globalErrors.isEmpty()) {
            return responseStatus(globalErrors.getFirst().tag(), errorTagMapping);
        }
        for (var edit : statusContext.editCollection()) {
            if (!edit.isOk()) {
                final var editErrors = edit.getEditErrors();
                if (editErrors != null && !editErrors.isEmpty()) {
                    return responseStatus(editErrors.getFirst().tag(), errorTagMapping);
                }
            }
        }
        return HttpResponseStatus.INTERNAL_SERVER_ERROR;
    }

    private static void deleteData(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        service.dataDELETE(new NettyServerRequest<>(params, callback) {
            @Override
            FullHttpResponse transform(final Empty result) {
                return simpleResponse(params, HttpResponseStatus.NO_CONTENT);
            }
        }, apiPath);
    }

}
