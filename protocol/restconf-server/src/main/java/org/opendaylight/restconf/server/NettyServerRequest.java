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

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.MappingServerRequest;

@NonNullByDefault
abstract class NettyServerRequest<T> extends MappingServerRequest<T> {
    final RequestParameters requestParams;

    private final RestconfRequest callback;

    NettyServerRequest(final RequestParameters requestParams, final RestconfRequest callback) {
        super(requestParams.queryParameters(), requestParams.defaultPrettyPrint(), requestParams.errorTagMapping());
        this.requestParams = requireNonNull(requestParams);
        this.callback = requireNonNull(callback);
    }

    @Override
    public final @Nullable Principal principal() {
        return requestParams.principal();
    }

    @Override
    protected final void onSuccess(final T result) {
        callback.onSuccess(transform(result));
    }

    @Override
    protected final void onFailure(final HttpStatusCode status, final FormattableBody body) {
        callback.onSuccess(responseBuilder(requestParams, HttpResponseStatus.valueOf(status.code()))
            .setBody(body)
            .build());
    }

    @Override
    public final @Nullable TransportSession session() {
        // FIXME: NETCONF-714: return the correct NettyTransportSession, RestconfSession in our case
        return null;
    }

    abstract FullHttpResponse transform(T result);
}
