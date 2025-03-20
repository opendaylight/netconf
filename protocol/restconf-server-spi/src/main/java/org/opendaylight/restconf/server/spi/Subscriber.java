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
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * A single subscriber to an {@link RestconfStream}.
 */
final class Subscriber<T> extends AbstractRegistration {
    private final @NonNull RestconfStream<T> stream;
    private final @NonNull Sender sender;
    private final @NonNull EventFormatter<T> formatter;
    private final @Nullable Uint32 subscriptionId;

    Subscriber(final Uint32 subscriptionId, final RestconfStream<T> stream, final Sender sender,
               final EventFormatter<T> formatter) {
        this.stream = requireNonNull(stream);
        this.sender = requireNonNull(sender);
        this.formatter = requireNonNull(formatter);
        this.subscriptionId = subscriptionId;
    }

    @NonNull EventFormatter<T> formatter() {
        return formatter;
    }

    @NonNull Sender sender() {
        return sender;
    }

    @Nullable Uint32 subscriptionId() {
        return subscriptionId;
    }

    @Override
    protected void removeRegistration() {
        stream.removeSubscriber(this);
    }
}
