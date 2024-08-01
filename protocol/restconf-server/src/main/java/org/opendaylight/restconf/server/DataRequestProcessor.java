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
import static org.opendaylight.restconf.server.PathParameters.DATA;
import static org.opendaylight.restconf.server.RequestUtils.extractApiPath;
import static org.opendaylight.restconf.server.RequestUtils.requestBody;
import static org.opendaylight.restconf.server.RequestUtils.serverRequest;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;
import static org.opendaylight.restconf.server.ResponseUtils.simpleErrorResponse;
import static org.opendaylight.restconf.server.ResponseUtils.simpleResponse;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.List;
import java.util.Objects;
import org.opendaylight.restconf.api.HttpStatusCode;
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
import org.opendaylight.restconf.server.api.PatchBody;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.ResourceBody;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerError;
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
            // GET /data(/.+)?
            if (apiPath.isEmpty()) {
                service.dataGET(getRequest(params, callback));
            } else {
                service.dataGET(getRequest(params, callback), apiPath);
            }
        } else if (HttpMethod.POST.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            // POST /data(/.+)?
            if (apiPath.isEmpty()) {
                service.dataPOST(postRequest(params, callback), childBody(params));
            } else {
                service.dataPOST(postRequest(params, callback), apiPath, dataPostBody(params));
            }
        } else if (HttpMethod.PUT.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            // PUT /data(/.+)?
            if (apiPath.isEmpty()) {
                service.dataPUT(putRequest(params, callback), dataResourceBody(params));
            } else {
                service.dataPUT(putRequest(params, callback), apiPath, dataResourceBody(params));
            }

        } else if (HttpMethod.PATCH.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            // PATCH /data(/.*)? RESTCONF patch case
            if (apiPath.isEmpty()) {
                service.dataPATCH(patchRequest(params, callback), dataResourceBody(params));
            } else {
                service.dataPATCH(patchRequest(params, callback), apiPath, dataResourceBody(params));
            }

        } else if (HttpMethod.PATCH.equals(method) && YANG_PATCH_TYPES.contains(contentType)) {
            // PATCH /data (yang-patch case)
            if (apiPath.isEmpty()) {
                service.dataPATCH(patchYangRequest(params, callback), dataPatchBody(params));
            } else {
                service.dataPATCH(patchYangRequest(params, callback), apiPath, dataPatchBody(params));
            }

        } else if (HttpMethod.DELETE.equals(method)) {
            // DELETE /data/.*
            service.dataDELETE(dataDelete(params, callback), apiPath);

        } else {
            callback.onSuccess(simpleErrorResponse(params, ErrorTag.DATA_MISSING));
        }
    }

    private static ChildBody childBody(final RequestParameters params) {
        return requestBody(params, JsonChildBody::new, XmlChildBody::new);
    }

    private static DataPostBody dataPostBody(final RequestParameters params) {
        return requestBody(params, JsonDataPostBody::new, XmlDataPostBody::new);
    }

    private static ResourceBody dataResourceBody(final RequestParameters params) {
        return requestBody(params, JsonResourceBody::new, XmlResourceBody::new);
    }

    private static PatchBody dataPatchBody(final RequestParameters params) {
        return requestBody(params, JsonPatchBody::new, XmlPatchBody::new);
    }

    private static ServerRequest<DataGetResult> getRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        return serverRequest(params, callback, result ->
            responseBuilder(params, HttpResponseStatus.OK)
                .setHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
                .setMetadataHeaders(result).setBody(result.body()).build());
    }

    private static <T extends DataPostResult> ServerRequest<T> postRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        return serverRequest(params, callback, result -> {
            if (result instanceof CreateResourceResult createResult) {
                final var location = params.basePath() + DATA + "/" + createResult.createdPath();
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

    private static ServerRequest<DataPutResult> putRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        return serverRequest(params, callback, result ->
            responseBuilder(params, result.created() ? HttpResponseStatus.CREATED : HttpResponseStatus.NO_CONTENT)
                .setMetadataHeaders(result)
                .build());
    }

    private static ServerRequest<DataPatchResult> patchRequest(final RequestParameters params,
        final FutureCallback<FullHttpResponse> callback) {
        return serverRequest(params, callback, result ->
            responseBuilder(params, HttpResponseStatus.OK)
                .setMetadataHeaders(result)
                .build());
    }

    private static ServerRequest<DataYangPatchResult> patchYangRequest(final RequestParameters params,
        final FutureCallback<FullHttpResponse> callback) {
        return serverRequest(params, callback, result -> {
            final var patchStatus = result.status();
            final var statusCode = statusOf(patchStatus);

            final var builder = responseBuilder(params, new HttpResponseStatus(statusCode.code(),
                Objects.requireNonNull(statusCode.phrase())))
                .setBody(new YangPatchStatusBody(patchStatus))
                .setMetadataHeaders(result);
            return builder.build();
        });
    }

    private static ServerRequest<Empty> dataDelete(final RequestParameters params,
        final FutureCallback<FullHttpResponse> callback) {
        return serverRequest(params, callback, result ->
            simpleResponse(params, HttpResponseStatus.NO_CONTENT));
    }

    private static HttpStatusCode statusOf(final PatchStatusContext result) {
        if (result.ok()) {
            return HttpStatusCode.OK;
        }
        final var globalErrors = result.globalErrors();
        if (globalErrors != null && !globalErrors.isEmpty()) {
            return statusOfFirst(globalErrors);
        }
        for (var edit : result.editCollection()) {
            if (!edit.isOk()) {
                final var editErrors = edit.getEditErrors();
                if (editErrors != null && !editErrors.isEmpty()) {
                    return statusOfFirst(editErrors);
                }
            }
        }
        return HttpStatusCode.INTERNAL_SERVER_ERROR;
    }

    private static HttpStatusCode statusOfFirst(final List<ServerError> errors) {
        ErrorTagMapping mapping = ErrorTagMapping.RFC8040;
        return mapping.statusOf(errors.getFirst().tag());
    }
}
