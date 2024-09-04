/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

/**
 * An {@link HTTPServer} operating over TLS.
 */
final class TlsHTTPServer extends HTTPServer {
    TlsHTTPServer(final TransportChannelListener listener, final AuthHandlerFactory authHandlerFactory) {
        super(listener, authHandlerFactory);
    }

    @Override
    void initializePipeline(final ChannelPipeline pipeline, final Http2ConnectionHandler connectionHandler) {
        // Application protocol negotiator over TLS
        pipeline.addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
                switch (protocol) {
                    case null -> throw new NullPointerException();
                    case ApplicationProtocolNames.HTTP_1_1 -> ctx.pipeline()
                        .addAfter(ctx.name(), null, new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH))
                        .addAfter(ctx.name(), null, new HttpServerKeepAliveHandler())
                        .replace(this, null, new HttpServerCodec());
                    case ApplicationProtocolNames.HTTP_2 -> ctx.pipeline().replace(this, null, connectionHandler);
                    default -> throw new IllegalStateException("unknown protocol: " + protocol);
                }
            }
        });
    }
}
