/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.yangtools.concepts.AbstractRegistration;

/**
 * A single subscriber to an {@link RestconfStream}.
 */
@NonNullByDefault
final class Subscriber<T> extends AbstractRegistration {
    private static final VarHandle EER_VH;
    private static final VarHandle SER_VH;

    static {
        final var lookup = MethodHandles.lookup();
        try {
            EER_VH = lookup.findVarHandle(Subscriber.class, "excludedEventRecords", long.class);
            SER_VH = lookup.findVarHandle(Subscriber.class, "sentEventRecords", long.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final RestconfStream<T> stream;
    private final EventFormatter<T> formatter;
    private final EventFilter<T> filter;
    private final Sender sender;

    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD")
    private long excludedEventRecords;
    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD")
    private long sentEventRecords;

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

    void sendDataMessage(final @Nullable String data) {
        if (data != null) {
            sender.sendDataMessage(data);
            SER_VH.getAndAdd(this, 1L);
        } else {
            EER_VH.getAndAdd(this, 1L);
        }
    }

    void endOfStream() {
        sender.endOfStream();
    }

    long excludedEventRecords() {
        return (long) EER_VH.getAcquire(this);
    }

    long sentEventRecords() {
        return (long) SER_VH.getAcquire(this);
    }

    @Override
    protected void removeRegistration() {
        stream.removeSubscriber(this);
    }
}
