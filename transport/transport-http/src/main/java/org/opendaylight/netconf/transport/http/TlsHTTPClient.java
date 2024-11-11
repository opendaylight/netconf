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
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

/**
 * An {@link HTTPClient} operating over TLS.
 */
final class TlsHTTPClient extends HTTPClient {
    TlsHTTPClient(final TransportChannelListener<? super HTTPTransportChannel> listener,
            final ClientAuthProvider authProvider, final boolean http2) {
        super(listener, HTTPScheme.HTTPS, authProvider, http2);
    }

    @Override
    void initializePipeline(final TransportChannel underlayChannel, final ChannelPipeline pipeline,
            final Http2ConnectionHandler connectionHandler) {
        // Application protocol negotiator over TLS
        pipeline.addLast(new ApplicationProtocolNegotiationHandler("") {
            @Override
            protected void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                    final var pipeline = ctx.pipeline();
                    pipeline.addLast(connectionHandler);
                    configureEndOfPipeline(underlayChannel, pipeline);
                    return;
                }
                ctx.close();
                throw new IllegalStateException("unknown protocol: " + protocol);
            }
        });
    }
}
