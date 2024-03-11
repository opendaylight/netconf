/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.InputStream;
import java.security.Principal;
import java.text.ParseException;
import java.util.function.Function;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.MappingServerRequest;

final class RequestUtils {

    private RequestUtils() {
        // hidden on purpose
    }

    static ApiPath extractApiPath(final RequestParameters params) {
        try {
            return ApiPath.parse(params.pathParameters().childIdentifier());
        } catch (ParseException e) {
            // FIXME use proper exception to build expected RESTCONF response
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    static <T> ServerRequest<T> serverRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback, final Function<T, FullHttpResponse> transformer) {

        return new MappingServerRequest<T>(params.queryParameters(), params.defaultPrettyPrint(),
                params.errorTagMapping()) {
            @Override
            public Principal principal() {
                return params.principal();
            }

            @Override
            protected void onSuccess(final T result) {
                callback.onSuccess(transformer.apply(result));
            }

            @Override
            protected void onFailure(final HttpStatusCode statusCode, final FormattableBody body) {
                final var status = statusCode == null
                    ? HttpResponseStatus.INTERNAL_SERVER_ERROR : HttpResponseStatus.valueOf(statusCode.code());
                callback.onSuccess(responseBuilder(params, status).setBody(body).build());
            }
        };
    }

    static <T extends ConsumableBody> T requestBody(final RequestParameters params,
            final Function<InputStream, T> jsonBodyBuilder, final Function<InputStream, T> xmlBodyBuilder) {
        return NettyMediaTypes.JSON_TYPES.contains(params.contentType())
            ? jsonBodyBuilder.apply(params.requestBody()) : xmlBodyBuilder.apply(params.requestBody());
    }
}
