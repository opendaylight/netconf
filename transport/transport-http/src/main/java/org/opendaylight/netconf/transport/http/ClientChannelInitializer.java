/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpClientCodec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev230417.HttpClientGrouping;

class ClientChannelInitializer extends ChannelInitializer<Channel> {

    private final ChannelHandler authHandler;
    private final ChannelHandler dispatcherHandler;

    ClientChannelInitializer(final HttpClientGrouping clientParams, final ChannelHandler dispatcherHandler) {
        this.authHandler = authHandler(requireNonNull(clientParams));
        this.dispatcherHandler = requireNonNull(dispatcherHandler);
    }

    @Override
    protected void initChannel(final Channel channel) throws Exception {
        channel.pipeline().addLast(new HttpClientCodec());
        if (authHandler != null) {
            channel.pipeline().addLast(authHandler);
        }
        channel.pipeline().addLast(dispatcherHandler);
    }

    private static ChannelHandler authHandler(final HttpClientGrouping clientParams) {
        return null;
    }
}
