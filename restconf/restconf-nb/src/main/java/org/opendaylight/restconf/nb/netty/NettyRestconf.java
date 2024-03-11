/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.netty;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.databind.netty.QueryParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyRestconf extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(NettyRestconf.class);

    private final RestconfServer server;

    public NettyRestconf(final RestconfServer server) {
        this.server = requireNonNull(server);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) throws Exception {
        final var method = msg.method();

        switch (method.name()) {
            case "GET" -> {
                LOG.debug("GET");
                dataGET(ctx, msg);
            }
            case "POST" -> LOG.debug("POST");
            case "PUT" -> LOG.debug("PUT");
            case "PATCH" -> LOG.debug("PATCH");
            case "DELETE" -> LOG.debug("DELETE");
            default -> LOG.debug("Unsupported request method.");
        }
    }

    public void dataGET(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        final var decoder = new QueryStringDecoder(msg.uri());
        completeDataGET(server.dataGET(QueryParams.newDataGetParams(decoder)), ctx);
    }

    private static void completeDataGET(final RestconfFuture<DataGetResult> future, final ChannelHandlerContext ctx) {
        future.addCallback(new NettyRestconfCallback<>(ctx) {
            @Override
            HttpResponse transform(final DataGetResult result) throws RestconfDocumentedException {
                final var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK,
                    Unpooled.copiedBuffer(result.payload().data().toString(), CharsetUtil.UTF_8));
                response.headers().set("ETag", result.entityTag());
                response.headers().set("Last-Modified", result.lastModified());
                return response;
            }
        });
    }
}
