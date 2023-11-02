/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * A {@link Stream} for delivering YANG 1.0 notifications.
 */
public final class NotificationStream extends Stream {
    private final @NonNull ImmutableSet<QName> notifications;

    NotificationStream(final String name, final Set<QName> notifications) {
        super(name);
        this.notifications = ImmutableSet.copyOf(notifications);
    }

    @NonNull Set<QName> notifications() {
        return notifications;
    }

    @Override
    ToStringHelper addToStringArguments(final ToStringHelper helper) {
        return super.addToStringArguments(helper).add("notifications", notifications);
    }
}
