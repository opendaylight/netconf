/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.opendaylight.restconf.server.NettyMediaTypes.RESTCONF_TYPES;
import static org.opendaylight.restconf.server.NettyMediaTypes.YANG_PATCH_TYPES;
import static org.opendaylight.restconf.server.RequestUtils.extractApiPath;
import static org.opendaylight.restconf.server.RequestUtils.requestBody;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;
import static org.opendaylight.restconf.server.ResponseUtils.responseStatus;
import static org.opendaylight.restconf.server.ResponseUtils.simpleErrorResponse;
import static org.opendaylight.restconf.server.ResponseUtils.simpleResponse;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
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
 * Static processor implementation serving {@code /data} path requests.
 */
final class DataRequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(DataRequestProcessor.class);

    private DataRequestProcessor() {
        // hidden on purpose
    }

    static void processDataRequest(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var method = params.method();
        final var contentType = params.contentType();
        final var apiPath = extractApiPath(params);

        if (HttpMethod.GET.equals(method)) {
            getData(params, service, callback, apiPath);
        } else if (HttpMethod.POST.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            postData(params, service, callback, apiPath);
        } else if (HttpMethod.PUT.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            putData(params, service, callback, apiPath);
        } else if (HttpMethod.PATCH.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            // RESTCONF patch case
            patchData(params, service, callback, apiPath);
        } else if (HttpMethod.PATCH.equals(method) && YANG_PATCH_TYPES.contains(contentType)) {
            // yang-patch case
            yangPatchData(params, service, callback, apiPath);
        } else if (HttpMethod.DELETE.equals(method)) {
            deleteData(params, service, callback, apiPath);
        } else {
            callback.onSuccess(simpleErrorResponse(params, ErrorTag.DATA_MISSING));
        }
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
                final var location = params.basePath() + PathParameters.DATA + "/" + createResult.createdPath();
                return responseBuilder(params, HttpResponseStatus.CREATED)
                    .setHeader(HttpHeaderNames.LOCATION, location)
                    .setMetadataHeaders(createResult).build();
            }
            if (result instanceof InvokeResult invokeResult) {
                final var output = invokeResult.output();
                return output == null ? simpleResponse(params, HttpResponseStatus.NO_CONTENT)
                    : responseBuilder(params, HttpResponseStatus.OK).setBody(output).build();
            }
            LOG.error("Unhandled result {}", result);
            return simpleResponse(params, HttpResponseStatus.INTERNAL_SERVER_ERROR);
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
