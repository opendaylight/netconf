/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * A {@link Sender} that manages subscription event streams over HTTP/1.
 */
public final class ChannelSenderSubscription extends AbstractChannelSender {
    private final RestconfStream<?> stream;
    private final RestconfStream.Registry streamRegistry;
    private final Uint32 subscriptionId;

    public ChannelSenderSubscription(final int sseMaximumFragmentLength, final RestconfStream<?> receiver,
        final RestconfStream.Registry streamRegistry, Uint32 subscriptionId) {
        super(sseMaximumFragmentLength);
        this.stream = receiver;
        this.streamRegistry = streamRegistry;
        this.subscriptionId = subscriptionId;
    }

    @Override
    public void sendDataMessage(final String data) {
        if (!data.isEmpty() && super.getCtx() != null) {
            super.sendDataMessage(data);
            stream.sink.updateSentEventRecords(streamRegistry, subscriptionId);
        }
    }
}
