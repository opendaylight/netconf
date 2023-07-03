/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for NETCONF server implementations working on top of a {@link TransportChannel}.
 */
public abstract class AbstractServerTransport implements TransportChannelListener {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractServerTransport.class);

    private final ServerChannelInitializer initializer;
    private final EventExecutor executor;

    protected AbstractServerTransport(final EventExecutor executor, final ServerChannelInitializer initializer) {
        this.initializer = requireNonNull(initializer);
        this.executor = requireNonNull(executor);
    }

    @Override
    public final void onTransportChannelEstablished(final TransportChannel channel) {
        LOG.debug("Transport channel {} established", channel);
        initializer.initialize(channel.channel(), executor.newPromise());
    }

    @Override
    public final void onTransportChannelFailed(final Throwable cause) {
        LOG.error("Transport channel failed", cause);
    }
}
