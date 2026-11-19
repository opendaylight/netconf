/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.databind.RequestException;

/**
 * A factory for {@link EventFilter}s.
 *
 * @since 12.0.3
 */
@NonNullByDefault
public abstract sealed class EventFilterFactory<T> permits EventFormatterFactory {

    // FIXME: no throws once both cases cannot throw
    public final EventFilter<T> newFilter(final FilterExpression expression) throws RequestException {
        return switch (expression) {
            case SubtreeFilterExpression subtree -> newFilter(subtree);
            case XPathFilterExpression xpath -> newFilter(xpath);
        };
    }

    // FIXME: 13.0.0: abstract and do not throw
    public EventFilter<T> newFilter(final SubtreeFilterExpression expression) throws RequestException {
        throw new RequestException(this.getClass().getCanonicalName() + " does not support subtree filters");
    }

    // FIXME: do not throw once we do not need to parse the expression
    public abstract EventFilter<T> newFilter(XPathFilterExpression expression) throws RequestException;
}
