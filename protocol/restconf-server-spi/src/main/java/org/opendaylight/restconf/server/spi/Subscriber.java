/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.yangtools.concepts.AbstractRegistration;

/**
 * A single subscriber to an {@link RestconfStream}.
 */
@NonNullByDefault
final class Subscriber<T> extends AbstractRegistration {
    private final RestconfStream<T> stream;
    private final EventFormatter<T> formatter;
    private final EventFilter<T> filter;
    private final Sender sender;

    Subscriber(final RestconfStream<T> stream, final Sender sender, final EventFormatter<T> formatter,
            final EventFilter<T> filter) {
        this.stream = requireNonNull(stream);
        this.sender = requireNonNull(sender);
        this.formatter = requireNonNull(formatter);
        this.filter = requireNonNull(filter);
    }

    EventFilter<T> filter() {
        return filter;
    }

    EventFormatter<T> formatter() {
        return formatter;
    }

    Sender sender() {
        return sender;
    }

    @Override
    protected void removeRegistration() {
        stream.removeSubscriber(this);
    }
}
