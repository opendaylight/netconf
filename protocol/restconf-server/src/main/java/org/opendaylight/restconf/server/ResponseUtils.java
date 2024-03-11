/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.restconf.server.api.YangErrorsBody;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ResponseUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ResponseUtils.class);

    private ResponseUtils() {
        // hidden on purpose
    }

    static void handleException(final FullHttpRequest request, final ErrorTagMapping errorTagMapping,
            final FutureCallback<FullHttpResponse> callback, final Exception thrown) {
        // TODO dispatcher thrown exception to formatted response
        LOG.error("exception on request dispatch", thrown);
        callback.onFailure(thrown);
    }

    static void handleException(final RequestParameters params, final FutureCallback<FullHttpResponse> callback,
            final Exception thrown) {
        // TODO service thrown exception to formatted response
        LOG.error("exception on request dispatch", thrown);
        callback.onFailure(thrown);
    }

    static FullHttpResponse simpleErrorResponse(final RequestParameters params, final ErrorTag errorTag) {
        // any error response should be returned with body
        // https://datatracker.ietf.org/doc/html/rfc8040#section-7.1
        final var statusCode = params.errorTagMapping().statusOf(errorTag).code();
        return responseBuilder(params, HttpResponseStatus.valueOf(statusCode))
            .setBody(new YangErrorsBody(List.of(
                new ServerError(ErrorType.PROTOCOL, errorTag, null, null, null, null))))
            .build();
    }

    static FullHttpResponse simpleResponse(final RequestParameters params, final HttpResponseStatus responseStatus) {
        return new DefaultFullHttpResponse(params.protocolVersion(), responseStatus);
    }

    static ResponseBuilder responseBuilder(final RequestParameters params, final HttpResponseStatus status) {
        return new ResponseBuilder(params, status);
    }

    public static final class ResponseBuilder {
        private final RequestParameters params;
        private final FullHttpResponse response;

        private ResponseBuilder(final RequestParameters params, final HttpResponseStatus status) {
            this.params = requireNonNull(params);
            response = new DefaultFullHttpResponse(params.protocolVersion(), status);
        }

        ResponseBuilder setHeader(final CharSequence name, final CharSequence value) {
            response.headers().set(name, value);
            return this;
        }

        ResponseBuilder setMetadataHeaders(final ConfigurationMetadata metadata) {
            final var etag = metadata.entityTag();
            if (etag != null) {
                response.headers().set(HttpHeaderNames.ETAG, etag.value());
            }
            final var lastModified = metadata.lastModified();
            if (lastModified != null) {
                response.headers().set(HttpHeaderNames.LAST_MODIFIED, DateFormatter.format(Date.from(lastModified)));
            }
            return this;
        }

        ResponseBuilder setBody(final FormattableBody body) {
            setContent(params, response, body);
            return this;
        }

        FullHttpResponse build() {
            return response;
        }
    }

    private static void setContent(final RequestParameters params, final FullHttpResponse response,
        final FormattableBody body) {
        final var contentType = responseTypeFromAccept(params);
        try (var out = new ByteBufOutputStream(response.content())) {
            if (NettyMediaTypes.APPLICATION_YANG_DATA_JSON.equals(contentType)) {
                body.formatToJSON(params.prettyPrint(), out);
            } else {
                body.formatToXML(params.prettyPrint(), out);
            }
            response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, contentType)
                .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Could not write content", e);
        }
    }

    private static AsciiString responseTypeFromAccept(final RequestParameters params) {
        final var acceptValues = params.requestHeaders().getAll(HttpHeaderNames.ACCEPT);
        if (acceptValues != null) {
            boolean isJson = false;
            boolean isXml = false;
            for (var accept : acceptValues) {
                for (var type : accept.toLowerCase(Locale.getDefault()).split(",")) {
                    final var acceptType = AsciiString.of(type);
                    isJson |= NettyMediaTypes.JSON_TYPES.contains(acceptType);
                    isXml |= NettyMediaTypes.XML_TYPES.contains(acceptType);
                }
            }
            if (isJson && !isXml) {
                return NettyMediaTypes.APPLICATION_YANG_DATA_JSON;
            }
            if (isXml && !isJson) {
                return NettyMediaTypes.APPLICATION_YANG_DATA_XML;
            }
        }
        return params.defaultAcceptType();
    }


}
