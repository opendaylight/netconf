/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import io.netty.channel.Channel;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.spi.UnderlayTransportChannel;

/**
 * A TCP {@link TransportChannel}.
 */
@NonNullByDefault
public final class TCPTransportChannel extends UnderlayTransportChannel {
    TCPTransportChannel(final Channel channel) {
        super(channel);
    }
}
