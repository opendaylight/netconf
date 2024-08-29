/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.transport.api.TransportChannel;

/**
 * A {@link TransportChannel} capable of exchanging {@link NetconfMessage}s.
 */
@NonNullByDefault
public final class NetconfChannel {
    private final TransportChannel transport;
    private final MessageEncoder messageEncoder;

    NetconfChannel(final TransportChannel transport, final MessageEncoder messageEncoder) {
        this.transport = requireNonNull(transport);
        this.messageEncoder = requireNonNull(messageEncoder);
    }

    public TransportChannel transport() {
        return transport;
    }

    public MessageEncoder messageEncoder() {
        return messageEncoder;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("transport", transport).toString();
    }
}
