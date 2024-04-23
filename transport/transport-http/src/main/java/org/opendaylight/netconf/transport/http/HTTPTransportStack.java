/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.AbstractOverlayTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

public abstract sealed class HTTPTransportStack extends AbstractOverlayTransportStack<HTTPTransportChannel>
        permits HTTPClient, HTTPServer {
    final HttpChannelInitializer channelInitializer;

    public HTTPTransportStack(final TransportChannelListener listener, final HttpChannelInitializer handler) {
        super(listener);
        this.channelInitializer = handler;
    }

    @Override
    protected void onUnderlayChannelEstablished(final @NonNull HTTPTransportChannel underlayChannel) {
        underlayChannel.channel().pipeline().addLast(channelInitializer);
        Futures.addCallback(channelInitializer.completeFuture(), new FutureCallback<>() {
            @Override
            public void onSuccess(final Void result) {
                addTransportChannel(new HTTPTransportChannel(underlayChannel));
            }

            @Override
            public void onFailure(Throwable cause) {
                notifyTransportChannelFailed(cause);
            }
        }, MoreExecutors.directExecutor());
    }
}
