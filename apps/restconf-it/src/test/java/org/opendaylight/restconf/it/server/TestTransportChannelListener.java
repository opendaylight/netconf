/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import static java.util.Objects.requireNonNull;

import java.util.function.Consumer;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.http.HTTPTransportChannel;

public final class TestTransportChannelListener implements TransportChannelListener<HTTPTransportChannel> {
    private final Consumer<HTTPTransportChannel> initializer;

    private volatile boolean initialized;

    public TestTransportChannelListener(final Consumer<HTTPTransportChannel> initializer) {
        this.initializer = requireNonNull(initializer);
    }

    public boolean initialized() {
        return initialized;
    }

    @Override
    public void onTransportChannelEstablished(final HTTPTransportChannel channel) {
        initializer.accept(channel);
        initialized = true;
    }

    @Override
    public void onTransportChannelFailed(final @NonNull Throwable cause) {
        throw new IllegalStateException("HTTP connection failure", cause);
    }
}