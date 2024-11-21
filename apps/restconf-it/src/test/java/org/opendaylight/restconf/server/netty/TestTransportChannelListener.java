/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import java.util.function.Consumer;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

final class TestTransportChannelListener implements TransportChannelListener<TransportChannel> {
    private final Consumer<Channel> initializer;

    private volatile boolean initialized;

    TestTransportChannelListener(final Consumer<Channel> initializer) {
        this.initializer = requireNonNull(initializer);
    }

    boolean initialized() {
        return initialized;
    }

    @Override
    public void onTransportChannelEstablished(final TransportChannel channel) {
        initializer.accept(channel.channel());
        initialized = true;
    }

    @Override
    public void onTransportChannelFailed(final @NonNull Throwable cause) {
        throw new IllegalStateException("HTTP connection failure", cause);
    }

    @Override
    public boolean transportChannelIsDone() {
        return false;
    }
}