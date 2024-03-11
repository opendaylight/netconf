/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

final class NettyResponseUtils {

    private NettyResponseUtils() {
        // utility class
    }

    static void setResponse(final FutureCallback<FullHttpResponse> callback,
            final FullHttpRequest request, final HttpResponseStatus responseStatus) {
        callback.onSuccess(
            new DefaultFullHttpResponse(request.protocolVersion(), responseStatus, Unpooled.EMPTY_BUFFER));
    }

    static void handleException(final RuntimeException thrown, final FullHttpRequest request,
            final FutureCallback<FullHttpResponse> callback) {
        // todo
    }
}
