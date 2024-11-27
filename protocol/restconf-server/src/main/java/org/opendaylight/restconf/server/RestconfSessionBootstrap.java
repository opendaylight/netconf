/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandlerContext;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.ConcurrentHTTPServerSession;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerSessionBootstrap;
import org.opendaylight.netconf.transport.http.PipelinedHTTPServerSession;
import org.opendaylight.netconf.transport.http.ServerSseHandler;

@NonNullByDefault
final class RestconfSessionBootstrap extends HTTPServerSessionBootstrap {
    private final EndpointRoot root;
    private final ServerSseHandler sseHandler;

    RestconfSessionBootstrap(final HTTPScheme scheme, final EndpointRoot root, final ServerSseHandler sseHandler) {
        super(scheme);
        this.root = requireNonNull(root);
        this.sseHandler = requireNonNull(sseHandler);
    }

    @Override
    protected PipelinedHTTPServerSession configureHttp1(final ChannelHandlerContext ctx) {
        ctx.pipeline().addBefore(ctx.name(), null, sseHandler);
        return new RestconfSession(scheme, root);
    }

    @Override
    protected ConcurrentHTTPServerSession configureHttp2(final ChannelHandlerContext ctx) {
        ctx.pipeline().addBefore(ctx.name(), null, sseHandler);
        return new RestconfSession(scheme, root);
    }
}
