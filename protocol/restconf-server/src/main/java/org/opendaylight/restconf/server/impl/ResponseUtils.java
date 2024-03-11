/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.common.errors.RestconfCallback;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ResponseUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ResponseUtils.class);

    private ResponseUtils() {
        // hidden on purpose
    }

    static void handleException(final RuntimeException thrown, final FullHttpRequest request,
            final FutureCallback<FullHttpResponse> callback) {
        // TODO exception to formatted response
        LOG.error("exception on request dispatch", thrown);
        callback.onFailure(thrown);
    }

    static void setStatusOnlyResponse(final RequestParameters params, final HttpResponseStatus responseStatus) {
        params.callback().onSuccess(
            new DefaultFullHttpResponse(params.request().protocolVersion(), responseStatus, Unpooled.EMPTY_BUFFER));
    }

    static void setResponse(final RequestParameters params, final Object resultObj, final HttpResponseStatus status) {
        setResponse(params, resultObj, status, Map.of());
    }

    static void setResponse(final RequestParameters params, final Object resultObj, final HttpResponseStatus status,
        final Map<CharSequence, Object> headers) {
        params.callback().onSuccess(buildResponse(params, resultObj, status, headers));
    }

    static <T> void setResponse(final RequestParameters params, final T resultObj,
        final Function<T, FormattableBody> bodyExtractor, final PrettyPrintParam prettyPrint) {
        setResponse(params, resultObj, bodyExtractor, prettyPrint, Map.of());
    }

    static <T> void setResponse(final RequestParameters params, final T resultObj,
            final Function<T, FormattableBody> bodyExtractor, final PrettyPrintParam prettyPrint,
            final Map<CharSequence, Object> headers) {
        params.callback().onSuccess(buildResponse(params, resultObj, bodyExtractor, prettyPrint, headers));
    }

    @SuppressWarnings("IllegalCatch")
    static <T> RestconfCallback<T> callback(final RequestParameters params, final Consumer<T> onSuccess) {
        return new RestconfCallback<T>() {
            @Override
            protected void onFailure(@NonNull RestconfDocumentedException failure) {
                handleException(failure, params.request(), params.callback());
            }

            @Override
            public void onSuccess(@NonNull T result) {
                try {
                    onSuccess.accept(result);
                } catch (RuntimeException e) {
                    handleException(e, params.request(), params.callback());
                }
            }
        };
    }

    static FullHttpResponse buildResponse(final RequestParameters params, final Object resultObj,
            final HttpResponseStatus status, final Map<CharSequence, Object> headers) {
        final var response = new DefaultFullHttpResponse(params.request().protocolVersion(), status);
        setHeaders(response, resultObj, headers);
        return response;
    }

    static <T> FullHttpResponse buildResponse(final RequestParameters params, final T resultObj,
        final Function<T, FormattableBody> bodyExtractor, final PrettyPrintParam prettyPrint) {
        return buildResponse(params, resultObj, bodyExtractor, prettyPrint, Map.of());
    }

    static <T> FullHttpResponse buildResponse(final RequestParameters params, final T resultObj,
            final Function<T, FormattableBody> bodyExtractor, final PrettyPrintParam prettyPrint,
            final Map<CharSequence, Object> headers) {
        final var content = bodyExtractor.apply(resultObj);
        final FullHttpResponse response;
        if (content != null) {
            // fixme limit buffer
            response = new DefaultFullHttpResponse(params.request().protocolVersion(), HttpResponseStatus.OK,
                Unpooled.buffer());
            setContent(params, response, content, prettyPrint);
        } else {
            response =  new DefaultFullHttpResponse(params.request().protocolVersion(), HttpResponseStatus.NO_CONTENT);
        }
        setHeaders(response, resultObj, headers);
        return response;
    }

    private static void setHeaders(final FullHttpResponse response,
            final Object resultObj, final Map<CharSequence, Object> headers) {
        if (resultObj instanceof ConfigurationMetadata metadata) {
            final var etag = metadata.entityTag();
            if (etag != null) {
                // TODO to confirm etag.weak() does not affect output value
                response.headers().set(HttpHeaderNames.ETAG, etag.value());
            }
            final var lastModified = metadata.lastModified();
            if (lastModified != null) {
                response.headers().set(HttpHeaderNames.LAST_MODIFIED, DateFormatter.format(Date.from(lastModified)));
            }
        }
        if (headers != null) {
            headers.forEach(response.headers()::set);
        }
    }

    private static void setContent(final RequestParameters params, final FullHttpResponse response,
            final FormattableBody body, final PrettyPrintParam prettyPrint) {
        final var contentType = responseTypeFromAccept(params);
        try (var out = new ByteBufOutputStream(response.content())) {
            if (ContentTypes.APPLICATION_YANG_DATA_JSON.equals(contentType)) {
                body.formatToJSON(prettyPrint, out);
            } else {
                body.formatToXML(prettyPrint, out);
            }
            response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, contentType)
                .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Could not write content", e);
        }
    }

    private static AsciiString responseTypeFromAccept(final RequestParameters params) {
        final var acceptValues = params.request().headers().getAll(HttpHeaderNames.ACCEPT);
        if (acceptValues != null) {
            boolean isJson = false;
            boolean isXml = false;
            for (var accept : acceptValues) {
                for (var type : accept.toLowerCase(Locale.getDefault()).split(",")) {
                    final var acceptType = AsciiString.of(type);
                    isJson |= ContentTypes.JSON_TYPES.contains(acceptType);
                    isXml |= ContentTypes.XML_TYPES.contains(acceptType);
                }
            }
            if (isJson && !isXml) {
                return ContentTypes.APPLICATION_YANG_DATA_JSON;
            }
            if (isXml && !isJson) {
                return ContentTypes.APPLICATION_YANG_DATA_XML;
            }
        }
        return params.defaultContentType();
    }
}
