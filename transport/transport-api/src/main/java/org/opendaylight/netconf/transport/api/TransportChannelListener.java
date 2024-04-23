/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Transport-level channel event listener.
 */
public interface TransportChannelListener<C extends TransportChannel> {
    /**
     * Invoked when a {@link TransportChannel} is established. Implementations of this method are expected to attach
     * to validate the channel and connect it to the messages layer.
     *
     * @param channel Established channel
     */
    void onTransportChannelEstablished(@NonNull C channel);

    /**
     * Invoked when a {@link TransportChannel} could not be established. Implementations of this method are expected
     * to react to this failure at least by logging it.
     *
     * @param cause Failure cause
     */
    void onTransportChannelFailed(@NonNull Throwable cause);
}
