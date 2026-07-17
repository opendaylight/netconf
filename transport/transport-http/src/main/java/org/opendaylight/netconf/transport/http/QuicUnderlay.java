/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicChannel;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.AbstractTransportStack;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * The QUIC connection established by {@link HTTPClient#connect} and its underlying UDP datagram channel, closed
 * together on {@link #shutdown()}.
 */
record QuicUnderlay(Channel datagramChannel, QuicChannel quicChannel) implements TransportStack {
    @Override
    public @NonNull ListenableFuture<Empty> shutdown() {
        return Futures.whenAllComplete(AbstractTransportStack.toListenableFuture(quicChannel.close()),
            AbstractTransportStack.toListenableFuture(datagramChannel.close()))
            .call(Empty::value, MoreExecutors.directExecutor());
    }
}
