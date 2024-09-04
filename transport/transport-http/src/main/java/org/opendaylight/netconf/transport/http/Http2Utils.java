/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames.STREAM_ID;

import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LogLevel;

/**
 * Collection of utility methods building HTTP/2 handlers.
 */
final class Http2Utils {

    private static final Http2FrameLogger CLIENT_FRAME_LOGGER = new Http2FrameLogger(LogLevel.INFO, "Client");
    private static final Http2FrameLogger SERVER_FRAME_LOGGER = new Http2FrameLogger(LogLevel.INFO, "Server");

    private Http2Utils() {
        // utility class
    }

    /**
     * Build external HTTP/2 to internal Http 1.1. adaptor handler.
     *
     * @param server true for server, false for client
     * @param maxContentLength max content length for http messages
     * @return connection handler instance
     */
    static Http2ConnectionHandler connectionHandler(final boolean server, final int maxContentLength) {
        final var connection = new DefaultHttp2Connection(server);
        final var frameListener = server
            ? new InboundHttp2ToHttpAdapterBuilder(connection)
                .maxContentLength(maxContentLength)
                .propagateSettings(true)
                .build()
            : Http2ToHttpAdapter.builder(connection)
                .maxContentLength(maxContentLength)
                .propagateSettings(true)
                .build();
        return new HttpToHttp2ConnectionHandlerBuilder()
            .frameListener(new DelegatingDecompressorFrameListener(connection, frameListener))
            .connection(connection)
            .frameLogger(server ? SERVER_FRAME_LOGGER : CLIENT_FRAME_LOGGER)
            .gracefulShutdownTimeoutMillis(0L)
            .build();
    }

    /**
     * Copies HTTP/2 associated stream id value (if exists) from one HTTP 1.1 message to another.
     *
     * @param from the message object to copy value from
     * @param to the message object to copy value to
     */
    static void copyStreamId(final HttpMessage from, final HttpMessage to) {
        final var streamId = from.headers().getInt(STREAM_ID.text());
        if (streamId != null) {
            to.headers().setInt(STREAM_ID.text(), streamId);
        }
    }
}
