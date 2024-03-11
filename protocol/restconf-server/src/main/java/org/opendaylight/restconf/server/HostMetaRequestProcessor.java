/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
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
import static org.opendaylight.restconf.server.ResponseUtils.unmappedRequestErrorResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import java.nio.charset.StandardCharsets;

/**
 * Static processor implementation serving root resource discovery requests.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-3.1">RFC 8040 Section 3.1</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6415#section-6">RFC 6415 Sections 6.1 - 6.2</a>
 */
final class HostMetaRequestProcessor {
    private static final String ALLOW_METHODS = allowHeaderValue(OPTIONS, HEAD, GET);

    @VisibleForTesting
    static String XRD_TEMPLATE = """
        <?xml version='1.0' encoding='UTF-8'?>
        <XRD xmlns="http://docs.oasis-open.org/ns/xri/xrd-1.0">
            <Link rel="restconf" href="%s"/>
        </XRD>""";

    @VisibleForTesting
    static String JRD_TEMPLATE = """
        {
            "links" : {
                "rel" : "restconf",
                "href" : "%s"
            }
        }""";

    private HostMetaRequestProcessor() {
        // hidden on purpose
    }

    static void processHostMetaRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        switch (params.method()) {
            case OPTIONS -> callback.onSuccess(optionsResponse(params, ALLOW_METHODS));
            // Root Resource Discovery as XRD.
            case HEAD, GET -> getHostMeta(params, callback, XRD_TEMPLATE, NettyMediaTypes.APPLICATION_XRD_XML);
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    static void processHostMetaJsonRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        switch (params.method()) {
            case OPTIONS -> callback.onSuccess(optionsResponse(params, ALLOW_METHODS));
            // Root Resource Discovery as a JRD.
            case HEAD, GET -> getHostMeta(params, callback, JRD_TEMPLATE, NettyMediaTypes.APPLICATION_JSON);
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    private static void getHostMeta(final RequestParameters params, final FutureCallback<FullHttpResponse> callback,
            final String template, final AsciiString contentType) {
        final var content = template.formatted(params.basePath()).getBytes(StandardCharsets.UTF_8);
        callback.onSuccess(ResponseUtils.simpleResponse(params, HttpResponseStatus.OK, contentType, content));
    }
}
