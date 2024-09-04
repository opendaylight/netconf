/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.security.Principal;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.MappingServerRequest;

@NonNullByDefault
final class NettyServerRequest<T> extends MappingServerRequest<T> {
    private final RequestParameters requestParameters;
    private final FutureCallback<FullHttpResponse> callback;
    private final Function<T, FullHttpResponse> transformer;

    NettyServerRequest(final RequestParameters requestParameters,
            final FutureCallback<FullHttpResponse> callback, final Function<T, FullHttpResponse> transformer) {
        super(requestParameters.queryParameters(), requestParameters.defaultPrettyPrint(),
            requestParameters.errorTagMapping());
        this.requestParameters = requireNonNull(requestParameters);
        this.callback = requireNonNull(callback);
        this.transformer = requireNonNull(transformer);
    }

    @Override
    public @Nullable Principal principal() {
        return requestParameters.principal();
    }

    @Override
    protected void onSuccess(final @NonNull T result) {
        callback.onSuccess(transformer.apply(result));
    }

    @Override
    protected void onFailure(final HttpStatusCode status, final FormattableBody body) {
        callback.onSuccess(responseBuilder(requestParameters, HttpResponseStatus.valueOf(status.code()))
            .setBody(body).build());
    }

    @Override
    public @Nullable TransportSession session() {
        // FIXME: return the correct NettyTransportSession
        return null;
    }
}
