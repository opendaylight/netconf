/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

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
    @Override
    @SuppressWarnings("unchecked")
    public final boolean matches(final EffectiveModelContext modelContext, final Object event) {
        return test(modelContext, (T) event);
    }

    protected abstract boolean test(EffectiveModelContext modelContext, T event);

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    protected abstract ToStringHelper addToStringAttributes(ToStringHelper helper);
}
