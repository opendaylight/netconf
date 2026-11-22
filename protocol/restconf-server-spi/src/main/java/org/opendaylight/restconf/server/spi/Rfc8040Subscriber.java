/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry.EventStreamFilter;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.yangtools.concepts.AbstractRegistration;

/**
 * A {@link Subscriber} established via the <a href="https://www.rfc-editor.org/rfc/rfc8040#section-6.3">RFC8040</a>
 * mechanism.
 *
 * @param <T> event type
 */
@NonNullByDefault
final class Rfc8040Subscriber<T> extends AbstractRegistration implements Subscriber<T> {
    private final RestconfStream<T> stream;
    private final Sender sender;
    private final EventFormatter<T> formatter;
    private final EventStreamFilter filter;

    Rfc8040Subscriber(final RestconfStream<T> stream, final Sender sender, final EventFormatter<T> formatter,
            final EventFilter<T> filter) {
        this.stream = requireNonNull(stream);
        this.sender = requireNonNull(sender);
        this.formatter = requireNonNull(formatter);
        this.filter = requireNonNull(filter);
    }

    @Override
    public EventStreamFilter filter() {
        return filter;
    }

    @Override
    public EventFormatter<T> formatter() {
        return formatter;
    }

    @Override
    public void sendDataMessage(final @Nullable String data) {
        if (data != null) {
            sender.sendDataMessage(data);
        }
    }

    @Override
    public void endOfStream() {
        sender.endOfStream();
    }

    @Override
    protected void removeRegistration() {
        stream.removeSubscriber(this);
    }
}
