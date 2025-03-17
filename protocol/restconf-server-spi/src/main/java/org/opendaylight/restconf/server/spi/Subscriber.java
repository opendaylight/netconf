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
import org.opendaylight.netconf.api.subtree.SubtreeFilter;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.yangtools.concepts.AbstractRegistration;

/**
 * A single subscriber to an {@link RestconfStream}.
 */
final class Subscriber<T> extends AbstractRegistration {
    private final @NonNull RestconfStream<T> stream;
    private final @NonNull Sender sender;
    private final @NonNull EventFormatter<T> formatter;
    private final @NonNull SubtreeFilter filter;

    Subscriber(final RestconfStream<T> stream, final Sender sender, final EventFormatter<T> formatter,
            final SubtreeFilter filter) {
        this.stream = requireNonNull(stream);
        this.sender = requireNonNull(sender);
        this.formatter = requireNonNull(formatter);
        // 2. add NETCONF-API SubtreeFilter
        // see how org.opendaylight.restconf.mdsal.spi.NotificationFormatter works
        // it has XML stream writer and operates on notification (NormalizedNode) Container body (just fillDocument)
        // use there DATABIND SubtreeFilter to construct SubtreeMatcher to match/filter
        // DATABIND SubtreeFilter can be written from NETCONF-API SubtreeFilter
        this.filter = requireNonNull(filter);
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
