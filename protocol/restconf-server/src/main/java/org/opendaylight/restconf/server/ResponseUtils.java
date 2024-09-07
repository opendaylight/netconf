/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.io.CountingOutputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.restconf.server.api.YangErrorsBody;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

final class ResponseUtils {
    @VisibleForTesting
    static final String UNMAPPED_REQUEST_ERROR = "Requested resource was not found.";
    @VisibleForTesting
    static final String UNSUPPORTED_MEDIA_TYPE_ERROR = "Request media type is not supported.";
    @VisibleForTesting
    static final String ENCODING_RESPONSE_ERROR = "Exception encoding response content. ";

    private static final ServerError UNMAPPED_REQUEST_SERVER_ERROR =
        new ServerError(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, UNMAPPED_REQUEST_ERROR);
    private static final ServerError UNSUPPORTED_ENCODING_SERVER_ERROR =
        new ServerError(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, UNSUPPORTED_MEDIA_TYPE_ERROR);
    private static final Splitter COMMA_SPLITTER = Splitter.on(',');

    private ResponseUtils() {
        // hidden on purpose
    }

    static String allowHeaderValue(final HttpMethod ... methods) {
        return String.join(", ", Stream.of(methods).map(HttpMethod::name).sorted().toList());
    }

    static FullHttpResponse optionsResponse(final RequestParameters params, final String allowHeaderValue) {
        return responseBuilder(params, HttpResponseStatus.OK)
            .setHeader(HttpHeaderNames.ALLOW, allowHeaderValue).build();
    }

    static FullHttpResponse unmappedRequestErrorResponse(final RequestParameters params) {
        return responseBuilder(params, responseStatus(ErrorTag.DATA_MISSING, params.errorTagMapping()))
            .setBody(new YangErrorsBody(UNMAPPED_REQUEST_SERVER_ERROR)).build();
    }

    static FullHttpResponse unsupportedMediaTypeErrorResponse(final RequestParameters params) {
        return responseBuilder(params, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE)
            .setBody(new YangErrorsBody(UNSUPPORTED_ENCODING_SERVER_ERROR)).build();
    }

    static FullHttpResponse simpleErrorResponse(final RequestParameters params, final ErrorTag errorTag,
            final String errorMessage) {
        // any error response should be returned with body
        // https://datatracker.ietf.org/doc/html/rfc8040#section-7.1
        return responseBuilder(params, responseStatus(errorTag, params.errorTagMapping()))
            .setBody(new YangErrorsBody(new ServerError(ErrorType.PROTOCOL, errorTag, errorMessage)))
            .build();
    }

    static HttpResponseStatus responseStatus(final ErrorTag errorTag, final ErrorTagMapping errorTagMapping) {
        final var statusCode = errorTagMapping.statusOf(errorTag).code();
        return HttpResponseStatus.valueOf(statusCode);
    }

    static FullHttpResponse simpleResponse(final RequestParameters params, final HttpResponseStatus responseStatus) {
        return new DefaultFullHttpResponse(params.protocolVersion(), responseStatus);
    }

    static FullHttpResponse simpleResponse(final RequestParameters params, final HttpResponseStatus responseStatus,
            final AsciiString contentType, final byte[] content) {
        return responseBuilder(params, responseStatus)
            .setBody(content).setHeader(HttpHeaderNames.CONTENT_TYPE, contentType).build();
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

        ResponseBuilder setBody(final byte[] bytes) {
            if (!HttpMethod.HEAD.equals(params.method())) {
                // don't write content if head only requested
                response.content().writeBytes(bytes);
            }
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
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
        try (var out = new CountingOutputStream(
            HttpMethod.HEAD.equals(params.method())
                // don't write content if head only requested
                ? OutputStream.nullOutputStream()
                : new ByteBufOutputStream(response.content()))) {
            if (NettyMediaTypes.APPLICATION_YANG_DATA_JSON.equals(contentType)) {
                body.formatToJSON(params.prettyPrint(), out);
            } else {
                body.formatToXML(params.prettyPrint(), out);
            }
            response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, contentType)
                .setInt(HttpHeaderNames.CONTENT_LENGTH, (int) out.getCount());
        } catch (IOException e) {
            throw new ServerErrorException(ErrorTag.OPERATION_FAILED,
                ENCODING_RESPONSE_ERROR + e.getMessage(), e);
        }
    }

    private static AsciiString responseTypeFromAccept(final RequestParameters params) {
        final var acceptTypes = extractAcceptTypes(params.requestHeaders());
        if (!acceptTypes.isEmpty()) {
            // if accept type is not defined or client accepts both xml and json types
            // the server configured default will be used
            boolean isJson = false;
            boolean isXml = false;
            for (var acceptType : acceptTypes) {
                isJson |= NettyMediaTypes.JSON_TYPES.contains(acceptType);
                isXml |= NettyMediaTypes.XML_TYPES.contains(acceptType);
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

    private static @NonNull List<AsciiString> extractAcceptTypes(final HttpHeaders headers) {
        final var acceptValues = headers.getAll(HttpHeaderNames.ACCEPT);
        if (acceptValues == null || acceptValues.isEmpty()) {
            return List.of();
        }
        final var list = new ArrayList<AsciiString>();
        for (var accept : acceptValues) {
            // use english locale lowercase to ignore possible case variants
            for (var type : COMMA_SPLITTER.split(accept.toLowerCase(Locale.ENGLISH))) {
                list.add(AsciiString.of(type.trim()));
            }
        }
        return list;
    }
}
