/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * A filter of events.
 *
 * @param <T> the type of events
 * @see AcceptingEventFilter
 * @see XPathEventFilter
 */
@NonNullByDefault
public abstract class EventFilter<T> implements AbstractRestconfStreamRegistry.EventStreamFilter {
    private final Class<T> eventType;

    /**
     * Default constructor.
     *
     * @param eventType the event type
     */
    protected EventFilter(final Class<T> eventType) {
        this.eventType = requireNonNull(eventType);
    }

    @Deprecated(since = "12.0.3", forRemoval = true)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected EventFilter() {
        this((Class) Object.class);
    }

    @Override
    public final boolean matches(final EffectiveModelContext modelContext, final Object event) {
        return test(modelContext, eventType.cast(event));
    }

    protected abstract boolean test(EffectiveModelContext modelContext, T event);

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    protected abstract ToStringHelper addToStringAttributes(ToStringHelper helper);
}
