/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http2.Http2Exception;
import java.net.SocketAddress;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerSession;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.DefaultTransportSession;

/**
 * A RESTCONF session, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8650#section-3.1">RFC8650</a>. It acts
 * as glue between a Netty channel and a RESTCONF server and may be servicing one (HTTP/1.1) or more (HTTP/2) logical
 * connections.
 */
final class RestconfSession extends HTTPServerSession {
    private final @NonNull DefaultTransportSession transportSession;
    private final @NonNull EndpointRoot root;

    @NonNullByDefault
    RestconfSession(final HTTPScheme scheme, final SocketAddress remoteAddress, final EndpointRoot root) {
        super(scheme);
        this.root = requireNonNull(root);
        transportSession = new DefaultTransportSession(new HttpDescription(scheme, remoteAddress));
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (cause instanceof Http2Exception.StreamException) {
            return;
        }
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        try {
            super.channelInactive(ctx);
        } finally {
            transportSession.close();
        }
    }

    @Override
    protected PreparedRequest prepareRequest(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers) {
        return root.prepare(transportSession, method, targetUri, headers);
    }

    @VisibleForTesting
    @NonNull TransportSession transportSession() {
        return transportSession;
    }
}
