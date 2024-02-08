/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.ChannelHandler;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.AbstractOverlayTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

public abstract sealed class HTTPTransportStack extends AbstractOverlayTransportStack<HTTPTransportChannel>
        permits HTTPClient, HTTPServer {
    final ChannelHandler handler;

    public HTTPTransportStack(final TransportChannelListener listener, final ChannelHandler handler) {
        super(listener);
        this.handler = handler;
    }

    @Override
    protected void onUnderlayChannelEstablished(final @NonNull TransportChannel underlayChannel) {
        underlayChannel.channel().pipeline().addLast(handler);
        addTransportChannel(new HTTPTransportChannel(underlayChannel));
    }
}
