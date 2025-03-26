/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

/**
 * A RESTCONF notification event stream. Created by {@link Registry#createLegacyStream}.
 * This stream is shut down and initiated for removal from global state after last subscriber is removed.
 *
 * @param <T> Type of processed events
 */
@Deprecated(since = "9.0.0", forRemoval = true)
public final class LegacyRestconfStream<T> extends RestconfStream<T> {
    public LegacyRestconfStream(final AbstractRestconfStreamRegistry registry, final Source<T> source,
            final String name) {
        super(registry, source, name);
    }

    @Override
    void onLastSubscriber() {
        terminate();
    }
}
