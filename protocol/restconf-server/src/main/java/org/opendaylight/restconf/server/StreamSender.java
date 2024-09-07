/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import org.opendaylight.restconf.server.spi.RestconfStream.Sender;

/**
 * A {@link Sender} bound to a logical stream. This is how event streams are delivered over HTTP/2: other requests can
 * be executed concurrently and the sender can be terminated when the stream is terminated.
 */
final class StreamSender implements Sender {
    private final Integer streamId;

    StreamSender(final Integer streamId) {
        this.streamId = requireNonNull(streamId);
    }

    @Override
    public void sendDataMessage(final String data) {
        // FIXME: finish this up
    }

    @Override
    public void endOfStream() {
        // FIXME: finish this up
    }

    void terminate() {
        // FIXME: finish this up
    }
}
