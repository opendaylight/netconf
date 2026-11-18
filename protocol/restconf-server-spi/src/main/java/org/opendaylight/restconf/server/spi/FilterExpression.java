/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.FilterSpec;
import org.opendaylight.yangtools.databind.DatabindContext;
import org.opendaylight.yangtools.databind.RequestException;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;

/**
 * An expression that can be used to filter data in its domain-specific object model.
 *
 * @since 12.0.3
 */
@NonNullByDefault
public sealed interface FilterExpression permits SubtreeFilterExpression, XPathFilterExpression {
    /**
     * {@return the {@link DatabindContext} used to create this expression}
     */
    DatabindContext databind();

    /**
     * {@return a {@link FilterExpression} corresponding to a {@link FilterSpec}}
     *
     * @param databind the {@link DatabindContext} to use
     * @param filterSpec the {@link FilterSpec} as a {@link ChoiceNode}
     * @throws RequestException if {@code filterSpec} cannot be bound
     */
    static FilterExpression ofFilterSpec(final DatabindContext databind, final ChoiceNode filterSpec)
            throws RequestException {
        return SubscriptionFilter.Definition.ofFilterSpec(filterSpec).bindTo(databind);
    }
}
