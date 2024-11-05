/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.util.AsciiString;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

/**
 * An {@link HTTPServer} operating over plain TCP.
 */
final class PlainHTTPServer extends HTTPServer {
    PlainHTTPServer(final TransportChannelListener<? super HTTPTransportChannel> listener,
            final AuthHandlerFactory authHandlerFactory) {
        super(listener, HttpScheme.HTTP, authHandlerFactory);
    }

    @Override
    void initializePipeline(final ChannelPipeline pipeline, final Http2ConnectionHandler connectionHandler) {
        // Cleartext upgrade flow
        final var sourceCodec = new HttpServerCodec();
        pipeline.addLast(new CleartextHttp2ServerUpgradeHandler(sourceCodec, new HttpServerUpgradeHandler(sourceCodec,
            protocol -> AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)
            ? new Http2ServerUpgradeCodec(connectionHandler) : null), connectionHandler));
    }
}
