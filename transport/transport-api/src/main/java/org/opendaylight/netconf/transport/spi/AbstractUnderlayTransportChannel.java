    /*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.spi;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.TransportChannel;

/**
 * An abstract base class for {@link TransportChannel}s wrapping Netty {@link Channel}.
 */
@NonNullByDefault
public abstract class AbstractUnderlayTransportChannel extends TransportChannel {
    private final Channel channel;

    protected AbstractUnderlayTransportChannel(final Channel channel) {
        this.channel = requireNonNull(channel);
    }

    @Override
    public final Channel channel() {
        return channel;
    }
}
