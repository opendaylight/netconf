/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import org.opendaylight.netconf.transport.api.AbstractOverlayTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

public abstract sealed class HTTPTransportStack extends AbstractOverlayTransportStack<HTTPTransportChannel>
        permits HTTPClient, HTTPServer {
    static final int MAX_HTTP_CONTENT_LENGTH = 16 * 1024;

    HTTPTransportStack(final TransportChannelListener listener) {
        super(listener);
    }

//    @Override
//    protected void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
//        underlayChannel.channel().pipeline().addLast(channelInitializer);
//        Futures.addCallback(channelInitializer.completeFuture(), new FutureCallback<>() {
//            @Override
//            public void onSuccess(final Void result) {
//                addTransportChannel(new HTTPTransportChannel(underlayChannel));
//            }
//
//            @Override
//            public void onFailure(final Throwable cause) {
//                notifyTransportChannelFailed(cause);
//            }
//        }, MoreExecutors.directExecutor());
//    }
}
