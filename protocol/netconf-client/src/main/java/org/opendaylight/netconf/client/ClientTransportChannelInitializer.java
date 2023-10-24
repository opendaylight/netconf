/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

/**
 *
 */
public class ClientTransportChannelInitializer implements TransportChannelListener {
    private final SettableFuture<NetconfClientSession> future = SettableFuture.create();
    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;

    public ClientTransportChannelInitializer(final NetconfClientSessionNegotiatorFactory negotiatorFactory) {
        this.negotiatorFactory = requireNonNull(negotiatorFactory);
    }

    public final ListenableFuture<NetconfClientSession> future() {
        return future;
    }

    @Override
    public void onTransportChannelEstablished(final TransportChannel channel) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onTransportChannelFailed(final Throwable cause) {
        // TODO Auto-generated method stub

    }
}
