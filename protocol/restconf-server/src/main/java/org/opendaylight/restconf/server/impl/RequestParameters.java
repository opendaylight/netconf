/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.io.InputStream;
import java.nio.charset.Charset;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * Request parameters. Provides parameters parsed from request object.
 */
@NonNullByDefault
class RequestParameters {
    private final String basePath;
    private final PathParameters pathParameters;
    private final AsciiString contentType;
    private final AsciiString defaultAcceptType;
    private final QueryParameters queryParameters;
    private final FullHttpRequest request;
    private final PrettyPrintParam defaultPrettyPrint;

    RequestParameters(final String basePath, final FullHttpRequest request, final AsciiString defaultAcceptType,
            final PrettyPrintParam defaultPrettyPrint) {
        this.basePath = basePath;
        this.request = request;
        this.defaultAcceptType = defaultAcceptType;
        this.defaultPrettyPrint = defaultPrettyPrint;

        final var mimeType = HttpUtil.getMimeType(request);
        contentType = mimeType == null ? AsciiString.EMPTY_STRING : AsciiString.of(mimeType);

        final var charset = HttpUtil.getCharset(request, Charset.defaultCharset());
        final var decoder = new QueryStringDecoder(request.uri(), charset);
        pathParameters = PathParameters.from(decoder.path(), basePath);
        queryParameters = QueryParameters.ofMultiValue(decoder.parameters());
    }

    /**
     * Returns the HTTP request method.
     *
     * @return HTTP request method
     */
    public HttpMethod method() {
        return request.method();
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
     * Returns base path of URI configured.
     *
     * @return base path value
     */
    public String basePath() {
        return basePath;
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

    public @NonNull HttpHeaders requestHeaders() {
        return request.headers();
    }

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

    public PrettyPrintParam prettyPrint() {
        final var requestParam = queryParameters.lookup(PrettyPrintParam.uriName, PrettyPrintParam::forUriValue);
        return requestParam != null ? requestParam : defaultPrettyPrint;
    }
}
