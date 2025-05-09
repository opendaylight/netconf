/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * An {@link EventFilter} accepting all events.
 */
@NonNullByDefault
final class AcceptingEventFilter<T> extends EventFilter<T> {
    private static final AcceptingEventFilter<?> INSTANCE = new AcceptingEventFilter<>();

    private AcceptingEventFilter() {
        // Hidden on purpose
    }

    @SuppressWarnings("unchecked")
    static <T> AcceptingEventFilter<T> instance() {
        return (AcceptingEventFilter<T>) INSTANCE;
    }

    @Override
    boolean matches(final EffectiveModelContext modelContext, final T event) {
        return true;
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper;
    }
}
