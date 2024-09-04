/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.opendaylight.netconf.transport.api.AbstractOverlayTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

public abstract sealed class HTTPTransportStack extends AbstractOverlayTransportStack<HTTPTransportChannel>
        permits HTTPClient, HTTPServer {
    private final HttpChannelInitializer channelInitializer;

    HTTPTransportStack(final TransportChannelListener listener, final HttpChannelInitializer channelInitializer) {
        super(listener);
        this.channelInitializer = requireNonNull(channelInitializer);
    }

    @Override
    protected void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        underlayChannel.channel().pipeline().addLast(channelInitializer);
        Futures.addCallback(channelInitializer.completeFuture(), new FutureCallback<>() {
            @Override
            public void onSuccess(final Void result) {
                addTransportChannel(new HTTPTransportChannel(underlayChannel));
            }

            @Override
            public void onFailure(final Throwable cause) {
                notifyTransportChannelFailed(cause);
            }
        }, MoreExecutors.directExecutor());
    }
}
