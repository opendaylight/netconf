/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.databind.subtree.SubtreeFilter;
import org.opendaylight.netconf.databind.subtree.SubtreeMatcher;
import org.opendaylight.restconf.server.spi.EventFilter;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * A {@link SubtreeFilter}-based notification filter.
 *
 * @since 12.0.3
 */
@NonNullByDefault
public final class NotificationSubtreeEventFilter extends EventFilter<DOMNotification> {
    private final SubtreeFilter filter;

    public NotificationSubtreeEventFilter(final SubtreeFilter filter) {
        super(DOMNotification.class);
        this.filter = requireNonNull(filter);
    }

    @Override
    protected boolean test(final EffectiveModelContext modelContext, final DOMNotification event) {
        final var path = event instanceof DOMNotificationEvent.Rfc7950 instanceEvent ? instanceEvent.path()
            : YangInstanceIdentifier.of();
        return SubtreeMatcher.permitsPath(filter, path) && new SubtreeMatcher(filter, event.getBody(), path).matches();
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("filter", filter.prettyTree());
    }
}
