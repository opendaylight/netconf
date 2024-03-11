/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.netty;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import java.nio.charset.StandardCharsets;
import javax.inject.Singleton;
import org.opendaylight.netconf.transport.http.RequestDispatcher;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class NettyRestconfDispatcher implements RequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(NettyRestconfDispatcher.class);

    private static final String RESPONSE_TEMPLATE = "Method: %s URI: %s Payload: %s";

    private final RestconfServer server;

    public NettyRestconfDispatcher(final RestconfServer server) {
        this.server = requireNonNull(server);
    }

    @Override
    public void dispatch(final FullHttpRequest request, final FutureCallback<FullHttpResponse> callback) {
        LOG.info("Dispatching request {}", request);

        // ECHO response. TODO update to actual one

        // return 200 response with a content built from request parameters
        final var method = request.method().name();
        final var uri = request.uri();
        final var payload = request.content().readableBytes() > 0
            ? request.content().toString(StandardCharsets.UTF_8) : "";
        final var responseMessage = RESPONSE_TEMPLATE.formatted(method, uri, payload);
        final var response = new DefaultFullHttpResponse(request.protocolVersion(), OK,
            wrappedBuffer(responseMessage.getBytes(StandardCharsets.UTF_8)));
        response.headers().set(CONTENT_TYPE, TEXT_PLAIN)
            .setInt(CONTENT_LENGTH, response.content().readableBytes());
        callback.onSuccess(response);
    }
}
