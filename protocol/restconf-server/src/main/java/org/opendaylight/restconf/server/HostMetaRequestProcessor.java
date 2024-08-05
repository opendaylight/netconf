/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static processor implementation serving {@code /host-meta} and {@code /host-meta.json} path requests.
 */
final class HostMetaRequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(HostMetaRequestProcessor.class);

    private HostMetaRequestProcessor() {
        // hidden on purpose
    }

    static void processHostMetaRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        //Root Resource Discovery as an XRD.
        //https://tools.ietf.org/html/rfc8040#section-3.1
        final var method = params.method();

        if (HttpMethod.GET.equals(method)) {
            LOG.debug("GET /host-meta");
            callback.onSuccess(ResponseUtils.responseBuilder(params, HttpResponseStatus.OK)
                .setHeader(HttpHeaderNames.CONTENT_TYPE, NettyMediaTypes.APPLICATION_XRD_XML)
                .setStringBody("<?xml version='1.0' encoding='UTF-8'?>\n"
                    + "<XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>\n"
                    + "  <Link rel='restconf' href='" + params.basePath() + "'/>\n"
                    + "</XRD>", NettyMediaTypes.APPLICATION_XRD_XML)
                .build());
        } else {
            LOG.debug("Unsupported method {}", method);
            callback.onSuccess(ResponseUtils.simpleErrorResponse(params, ErrorTag.OPERATION_NOT_SUPPORTED));
        }
    }

    static void processHostMetaJsonRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        //Root Resource Discovery as a JRD.
        //https://tools.ietf.org/html/rfc6415#appendix-A
        final var method = params.method();

        if (HttpMethod.GET.equals(method)) {
            LOG.debug("GET /host-meta.json");
            callback.onSuccess(ResponseUtils.responseBuilder(params, HttpResponseStatus.OK)
                .setStringBody("{\n"
                    + "  \"links\" : {\n"
                    + "    \"rel\" : \"restconf\",\n"
                    + "    \"href\" : \"" + params.basePath() + "\"\n"
                    + "  }\n"
                    + "}", NettyMediaTypes.APPLICATION_YANG_DATA_JSON)
                .build());
        } else {
            LOG.debug("Unsupported method {}", method);
            callback.onSuccess(ResponseUtils.simpleErrorResponse(params, ErrorTag.OPERATION_NOT_SUPPORTED));
        }
    }
}
