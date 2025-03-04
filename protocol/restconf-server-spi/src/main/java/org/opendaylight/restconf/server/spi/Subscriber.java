/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.yangtools.concepts.AbstractRegistration;

/**
 * A single subscriber to an {@link RestconfStream}.
 */
final class Subscriber<T> extends AbstractRegistration {
    private final @NonNull RestconfStream<T> stream;
    private final @NonNull Sender sender;
    private final @NonNull EventFormatter<T> formatter;
    private final @NonNull ReceiverImpl receiver;

    Subscriber(final RestconfStream<T> stream, final Sender sender, final EventFormatter<T> formatter,
        final @NonNull ReceiverImpl receiver) {
        this.stream = requireNonNull(stream);
        this.sender = requireNonNull(sender);
        this.formatter = requireNonNull(formatter);
        this.receiver = requireNonNull(receiver);
    }

    @NonNull ReceiverImpl receiver() {
        return receiver;
    }

    @NonNull EventFormatter<T> formatter() {
        return formatter;
    }

    @NonNull Sender sender() {
        return sender;
    }

    @Override
    protected void removeRegistration() {
        stream.removeSubscriber(this);
    }
}
