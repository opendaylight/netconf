/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.ServerRequest;

/**
 * Request parameters. Aggregates request and configuration parameters needed to process the request.
 */
public class RequestParameters {
    private final String basePath;
    private final PathParameters pathParameters;
    private final AsciiString contentType;
    private final AsciiString defaultContentType;
    private final Map<String, List<String>> queryParameters;
    private final FullHttpRequest request;
    private final FutureCallback<FullHttpResponse> callback;
    private final PrettyPrintParam defaultPrettyPrint;

    public RequestParameters(final String basePath, final FullHttpRequest request,
            final FutureCallback<FullHttpResponse> callback, final AsciiString defaultContentType,
            final PrettyPrintParam defaultPrettyPrint) {
        this.basePath = basePath;
        this.request = request;
        this.callback = callback;
        this.defaultContentType = defaultContentType;
        this.defaultPrettyPrint = defaultPrettyPrint;

        final var mimeType = HttpUtil.getMimeType(request);
        contentType = mimeType == null ? AsciiString.EMPTY_STRING : AsciiString.of(mimeType);

        final var charset = HttpUtil.getCharset(request, Charset.defaultCharset());
        final var decoder = new QueryStringDecoder(request.uri(), charset);
        pathParameters = PathParameters.from(decoder.path(), basePath);
        queryParameters = decoder.parameters();
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
    public PathParameters pathParameters() {
        return pathParameters;
    }

    /**
     * Returns query parameters as multi-value map.
     *
     * @return query parameters
     */
    public Map<String, List<String>> queryParameters() {
        return queryParameters;
    }

    /**
     * Returns request object.
     *
     * @return request object
     */
    public FullHttpRequest request() {
        return request;
    }

    /**
     * Returns transport layer callback object, used for response delivery.
     *
     * @see org.opendaylight.netconf.transport.http.RequestDispatcher#dispatch(FullHttpRequest, FutureCallback)
     *
     * @return transport layer callback
     */
    public FutureCallback<FullHttpResponse> callback() {
        return callback;
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
     *
     * @return default value configured
     */
    public @NonNull AsciiString defaultContentType() {
        return defaultContentType;
    }

    /**
     * Returns configured default value for pretty print parameter.
     *
     * @see ServerRequest#prettyPrint()
     *
     * @return default value configured
     */
    public PrettyPrintParam defaultPrettyPrint() {
        return defaultPrettyPrint;
    }
}
