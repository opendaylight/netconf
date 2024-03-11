/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;

/**
 * Request parameters. Provides parameters parsed from request object.
 */
@NonNullByDefault
final class RequestParameters {
    private final URI baseUri;
    private final PathParameters pathParameters;
    private final AsciiString contentType;
    private final AsciiString defaultAcceptType;
    private final QueryParameters queryParameters;
    private final FullHttpRequest request;
    private final ErrorTagMapping errorTagMapping;
    private final Principal principal;
    private final PrettyPrintParam defaultPrettyPrint;

    RequestParameters(final URI baseUri, final FullHttpRequest request, final @Nullable Principal principal,
        final ErrorTagMapping errorTagMapping, final AsciiString defaultAcceptType,
        final PrettyPrintParam defaultPrettyPrint) {
        this.baseUri = baseUri;
        this.request = request;
        this.principal = principal;
        this.errorTagMapping = errorTagMapping;
        this.defaultAcceptType = defaultAcceptType;
        this.defaultPrettyPrint = defaultPrettyPrint;

        contentType = extractContentType(request, defaultAcceptType);
        final var decoder = new QueryStringDecoder(request.uri(), StandardCharsets.UTF_8);
        pathParameters = PathParameters.from(decoder.path(), baseUri.getPath());
        queryParameters = QueryParameters.ofMultiValue(decoder.parameters());
    }

    /**
     * Returns enum representation of HTTP request method.
     *
     * @return request method
     */
    public Method method() {
        return Method.of(request.method());
    }

    /**
     * Returns content-type header value if defined.
     *
     * @return content-type value as {@link AsciiString} if not defined, null otherwise
     */
    public AsciiString contentType() {
        return contentType;
    }

    /**
     * Returns base URI configured.
     *
     * @return base URI value
     */
    public URI baseUri() {
        return baseUri;
    }

    /**
     * Returns base path of URI configured.
     *
     * @return base path value
     */
    public String basePath() {
        return baseUri.getPath();
    }

    /**
     * Path parameters extracted from request URI.
     *
     * @return path parameters
     * @see PathParameters
     */
    public @NonNull PathParameters pathParameters() {
        return pathParameters;
    }

    /**
     * Returns query parameters.
     *
     * @return query parameters
     */
    public @NonNull QueryParameters queryParameters() {
        return queryParameters;
    }

    /**
     * Returns request headers.
     *
     * @return request headers
     */
    public @NonNull HttpHeaders requestHeaders() {
        return request.headers();
    }

    /**
     * Returns HTTP protocol version.
     *
     * @return HTTP protocol version
     */
    public @NonNull HttpVersion protocolVersion() {
        return request.protocolVersion();
    }

    /**
     * Returns request body (content).
     *
     * @return request body as {@link InputStream}
     */
    public @NonNull InputStream requestBody() {
        return new ByteBufInputStream(request.content());
    }

    /**
     * Returns configured default content-type value for response encoding.
     * To be used if 'accept' header is absent.
     *
     * @return default value configured
     */
    public AsciiString defaultAcceptType() {
        return defaultAcceptType;
    }

    /**
     * Returns configured default value for pretty print parameter.
     *
     * @return default value configured
     */
    public PrettyPrintParam defaultPrettyPrint() {
        return defaultPrettyPrint;
    }

    /**
     * Returns pretty print parameter value to be used for current request .
     *
     * @return parameter value
     */
    public PrettyPrintParam prettyPrint() {
        final var requestParam = queryParameters.lookup(PrettyPrintParam.uriName, PrettyPrintParam::forUriValue);
        return requestParam != null ? requestParam : defaultPrettyPrint;
    }

    /**
     * Returns error tag mapping configured.
     *
     * @return error tag mapping instance
     */
    public ErrorTagMapping errorTagMapping() {
        return errorTagMapping;
    }

    /**
     * Returns user principal associated with current request.
     *
     * @return principal object if defined, null otherwise
     */
    public @Nullable Principal principal() {
        return principal;
    }

    private static AsciiString extractContentType(final FullHttpRequest request, final AsciiString defaultType) {
        final var contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType != null) {
            return AsciiString.of(contentType);
        }
        // when request body is empty content-type value plays no role, and eligible to be absent,
        // in this case apply default type to prevent unsupported media type error when checked subsequently
        return request.content().readableBytes() == 0 ? defaultType : AsciiString.EMPTY_STRING;
    }

    /**
     * Enum representation of {@link HttpMethod} in order for easier use with switch function.
     */
    @NonNullByDefault
    enum Method {
        GET, POST, PUT, PATCH, DELETE, UNSUPPORTED;

        static Method of(final HttpMethod httpMethod) {
            for (var method : values()) {
                if (method.name().equals(httpMethod.name())) {
                    return method;
                }
            }
            return UNSUPPORTED;
        }
    }
}
