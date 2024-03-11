/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.AsciiString;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.ServerRequest;

/**
 * HTTP request context.
 * Aggregates request and configuration parameters and service instances needed to serve the requestprocessing.
 */
interface RequestContext {

    /**
     * Returns the HTTP request method.
     *
     * @return HTTP request method
     */
    @NonNull HttpMethod method();

    /**
     * Returns content-type header value if defined.
     *
     * @return content-type value as {@link AsciiString} if not defined, null otherwise
     */
    @Nullable AsciiString contentType();

    /**
     * Returns base path of URI configured.
     *
     * @return base path value
     */
    @NonNull String basePath();

    /**
     * Returns the context path which is the part of URI following base path excluding request parameters.
     * If request URI does not start with configured base path the context path value will be empty string.
     *
     * @return context path value
     */
    @NonNull String contextPath();

    /**
     * Indicates context contains no-empty context path value.
     *
     * @see #contextPath()
     *
     * @return true if context path value is not empty, false otherwise
     */
    boolean hasContextPath();

    /**
     * Returns query parameters as multivalue map.
     *
     * @return query parameters
     */
    @NonNull Map<String, List<String>> queryParameters();

    /**
     * Returns request body (content).
     *
     * @return request body as {@link InputStream}
     */
    @NonNull InputStream requestBody();

    /**
     * Returns request object.
     *
     * @return request object
     */
    @NonNull FullHttpRequest request();

    /**
     * Returns transport layer callback object, used for response delivery.
     *
     * @see org.opendaylight.netconf.transport.http.RequestDispatcher#dispatch(FullHttpRequest, FutureCallback)
     *
     * @return transport layer callback
     */
    @NonNull FutureCallback<FullHttpResponse> callback();

    /**
     * Returns configured default value for pretty print parameter.
     *
     * @see ServerRequest#prettyPrint()
     *
     * @return default value configured
     */
    @NonNull PrettyPrintParam defaultPrettyPrint();

    /**
     * Returns configured default content-type value for response encoding.
     *
     * @return default value configured
     */
    @NonNull AsciiString defaultContentType();
}
