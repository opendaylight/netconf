/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import com.google.common.base.MoreObjects;
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

public class DefaultContext implements RequestContext {
    private final HttpMethod method;
    private final String contextPath;
    private final Charset charset;
    private final AsciiString contentType;
    private final AsciiString defaultContentType;
    private final Map<String, List<String>> queryParameters;
    private final FullHttpRequest request;
    private final FutureCallback<FullHttpResponse> callback;
    private final PrettyPrintParam defaultPrettyPrint;

    public DefaultContext(final String basePath, final FullHttpRequest request,
            final FutureCallback<FullHttpResponse> callback, final AsciiString defaultContentType,
            final PrettyPrintParam defaultPrettyPrint) {
        this.request = request;
        this.callback = callback;
        this.defaultContentType = defaultContentType;
        this.defaultPrettyPrint = defaultPrettyPrint;

        method = request.method();
        final var mimeType = HttpUtil.getMimeType(request);
        contentType = mimeType == null ? AsciiString.EMPTY_STRING : AsciiString.of(mimeType);

        charset = HttpUtil.getCharset(request, Charset.defaultCharset());
        final var decoder = new QueryStringDecoder(request.uri(), charset);
        final var reqPath = decoder.path();
        contextPath = reqPath.startsWith(basePath) ? reqPath.substring(basePath.length()) : "";
        queryParameters = decoder.parameters();
    }

    @Override
    public HttpMethod method() {
        return method;
    }

    @Override
    public AsciiString contentType() {
        return contentType;
    }

    @Override
    public String contextPath() {
        return contextPath;
    }

    @Override
    public Map<String, List<String>> queryParameters() {
        return queryParameters;
    }

    @Override
    public FullHttpRequest request() {
        return request;
    }

    @Override
    public FutureCallback<FullHttpResponse> callback() {
        return callback;
    }

    @Override
    public @NonNull InputStream requestBody() {
        return new ByteBufInputStream(request.content());
    }

    @Override
    public boolean hasContextPath() {
        return !contextPath.isEmpty();
    }

    @Override
    public @NonNull AsciiString defaultContentType() {
        return defaultContentType;
    }

    @Override
    public PrettyPrintParam defaultPrettyPrint() {
        return defaultPrettyPrint;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("method", method)
            .add("contextPath", contextPath)
            .add("charset", charset)
            .add("contentType", contentType)
            .add("defaultContentType", defaultContentType)
            .add("queryParameters", queryParameters)
            .add("request", request)
            .add("callback", callback)
            .add("defaultPrettyPrint", defaultPrettyPrint)
            .toString();
    }
}
