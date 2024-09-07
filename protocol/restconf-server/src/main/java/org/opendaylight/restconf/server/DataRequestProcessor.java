/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpMethod.OPTIONS;
import static io.netty.handler.codec.http.HttpMethod.PATCH;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static org.opendaylight.restconf.server.NettyMediaTypes.ACCEPT_PATCH_HEADER_VALUE;
import static org.opendaylight.restconf.server.NettyMediaTypes.RESTCONF_TYPES;
import static org.opendaylight.restconf.server.NettyMediaTypes.YANG_PATCH_TYPES;
import static org.opendaylight.restconf.server.RequestUtils.extractApiPath;
import static org.opendaylight.restconf.server.RequestUtils.requestBody;
import static org.opendaylight.restconf.server.ResponseUtils.allowHeaderValue;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;
import static org.opendaylight.restconf.server.ResponseUtils.responseStatus;
import static org.opendaylight.restconf.server.ResponseUtils.simpleErrorResponse;
import static org.opendaylight.restconf.server.ResponseUtils.simpleResponse;
import static org.opendaylight.restconf.server.ResponseUtils.unmappedRequestErrorResponse;
import static org.opendaylight.restconf.server.ResponseUtils.unsupportedMediaTypeErrorResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.ChildBody;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostBody;
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

/**
 * Static request processor serving RESTCONF and Yang-Patch requests for data resource.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-3.3.1">RFC 8040.
 * Section 3.3.1. {+restconf}/data</a>
 */
final class DataRequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(DataRequestProcessor.class);

    @VisibleForTesting
    static final String ALLOW_METHODS_ROOT = allowHeaderValue(OPTIONS, HEAD, GET, POST, PUT, PATCH);
    @VisibleForTesting
    static final String ALLOW_METHODS = allowHeaderValue(OPTIONS, HEAD, GET, POST, PUT, PATCH, DELETE);

    private DataRequestProcessor() {
        // hidden on purpose
    }

    static void processDataRequest(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var contentType = params.contentType();
        final var apiPath = extractApiPath(params);
        switch (params.method().name()) {
            // resource options -> https://datatracker.ietf.org/doc/html/rfc8040#section-4.1
            case "OPTIONS" -> options(params, callback, apiPath);
            // retrieve data and metadata for a resource -> https://datatracker.ietf.org/doc/html/rfc8040#section-4.3
            // HEAD is same as GET but without content -> https://datatracker.ietf.org/doc/html/rfc8040#section-4.2
            case "HEAD", "GET" -> getData(params, service, callback, apiPath);
            case "POST" -> {
                if (RESTCONF_TYPES.contains(contentType)) {
                    // create resource -> https://datatracker.ietf.org/doc/html/rfc8040#section-4.4.1
                    // or invoke an action -> https://datatracker.ietf.org/doc/html/rfc8040#section-3.6
                    postData(params, service, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            case "PUT" -> {
                if (RESTCONF_TYPES.contains(contentType)) {
                    // create or replace target resource -> https://datatracker.ietf.org/doc/html/rfc8040#section-4.5
                    putData(params, service, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            case "PATCH" -> {
                if (RESTCONF_TYPES.contains(contentType)) {
                    // Plain RESTCONF patch = merge target resource content ->
                    // https://datatracker.ietf.org/doc/html/rfc8040#section-4.6.1
                    patchData(params, service, callback, apiPath);
                } else if (YANG_PATCH_TYPES.contains(contentType)) {
                    // YANG Patch = ordered list of edits that are applied to the target datastore ->
                    // https://datatracker.ietf.org/doc/html/draft-ietf-netconf-yang-patch-14#section-2
                    yangPatchData(params, service, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            // delete target resource -> https://datatracker.ietf.org/doc/html/rfc8040#section-4.7
            case "DELETE" -> deleteData(params, service, callback, apiPath);
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    private static void options(final RequestParameters params, final FutureCallback<FullHttpResponse> callback,
            final ApiPath apiPath) {
        callback.onSuccess(
            responseBuilder(params, HttpResponseStatus.OK)
                .setHeader(HttpHeaderNames.ALLOW, apiPath.isEmpty() ? ALLOW_METHODS_ROOT : ALLOW_METHODS)
                .setHeader(HttpHeaderNames.ACCEPT_PATCH, ACCEPT_PATCH_HEADER_VALUE).build());
    }

    private static void getData(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataGetResult>(params, callback,
            result -> responseBuilder(params, HttpResponseStatus.OK)
                .setHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
                .setMetadataHeaders(result).setBody(result.body()).build());
        if (apiPath.isEmpty()) {
            service.dataGET(request);
        } else {
            service.dataGET(request, apiPath);
        }
    }

    private static void postData(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        if (apiPath.isEmpty()) {
            final ChildBody childBody = requestBody(params, JsonChildBody::new, XmlChildBody::new);
            service.dataPOST(postRequest(params, callback), childBody);
        } else {
            final DataPostBody dataPostBody = requestBody(params, JsonDataPostBody::new, XmlDataPostBody::new);
            service.dataPOST(postRequest(params, callback), apiPath, dataPostBody);
        }
    }

    private static <T extends DataPostResult> ServerRequest<T> postRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        return new NettyServerRequest<>(params, callback, result -> {
            if (result instanceof CreateResourceResult createResult) {
                final var location = params.baseUri() + PathParameters.DATA + "/" + createResult.createdPath();
                return responseBuilder(params, HttpResponseStatus.CREATED)
                    .setHeader(HttpHeaderNames.LOCATION, location)
                    .setMetadataHeaders(createResult).build();
            }
            if (result instanceof InvokeResult invokeResult) {
                final var output = invokeResult.output();
                return output == null ? simpleResponse(params, HttpResponseStatus.NO_CONTENT)
                    : responseBuilder(params, HttpResponseStatus.OK).setBody(output).build();
            }
            // below is not expected
            LOG.error("Unexpected response {}", result);
            return simpleErrorResponse(params, ErrorTag.OPERATION_FAILED,
                "Internal error. See server logs for details");
        });
    }

    private static void putData(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataPutResult>(params, callback,
            result -> {
                final var status = result.created() ? HttpResponseStatus.CREATED : HttpResponseStatus.NO_CONTENT;
                return responseBuilder(params, status).setMetadataHeaders(result).build();
            });
        final var dataResourceBody = requestBody(params, JsonResourceBody::new, XmlResourceBody::new);
        if (apiPath.isEmpty()) {
            service.dataPUT(request, dataResourceBody);
        } else {
            service.dataPUT(request, apiPath, dataResourceBody);
        }
    }

    private static void patchData(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataPatchResult>(params, callback, result ->
            responseBuilder(params, HttpResponseStatus.OK).setMetadataHeaders(result).build());
        final var dataResourceBody = requestBody(params, JsonResourceBody::new, XmlResourceBody::new);
        if (apiPath.isEmpty()) {
            service.dataPATCH(request, dataResourceBody);
        } else {
            service.dataPATCH(request, apiPath, dataResourceBody);
        }
    }

    private static void yangPatchData(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataYangPatchResult>(params, callback,
            result -> {
                final var patchStatus = result.status();
                final var status = patchResponseStatus(patchStatus, params.errorTagMapping());
                return responseBuilder(params, status)
                    .setBody(new YangPatchStatusBody(patchStatus))
                    .setMetadataHeaders(result).build();
            });
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
        final var request = new NettyServerRequest<Empty>(params, callback,
            result -> simpleResponse(params, HttpResponseStatus.NO_CONTENT));
        service.dataDELETE(request, apiPath);
    }
}
