/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicChannel;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * The QUIC connection established by {@link HTTPClient#connect} and its underlying UDP datagram channel, closed
 * together on {@link #shutdown()}.
 */
@NonNullByDefault
record ClientQuicUnderlay(Channel datagramChannel, QuicChannel quicChannel) implements TransportStack {
    ClientQuicUnderlay {
        requireNonNull(datagramChannel);
        requireNonNull(quicChannel);
    }

    @Override
    public ListenableFuture<Empty> shutdown() {
        return Futures.whenAllComplete(HTTPClient.closeChannel(quicChannel), HTTPClient.closeChannel(datagramChannel))
            .call(Empty::value, MoreExecutors.directExecutor());
    }
}
