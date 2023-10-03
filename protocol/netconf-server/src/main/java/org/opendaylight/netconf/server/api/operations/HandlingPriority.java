/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.operations;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNull;

public final class HandlingPriority implements Comparable<HandlingPriority> {
    // FIXME: remote this constant
    public static final @NonNull HandlingPriority HANDLE_WITH_DEFAULT_PRIORITY =
        new HandlingPriority(Integer.MIN_VALUE);
    public static final @NonNull HandlingPriority HANDLE_WITH_MAX_PRIORITY = new HandlingPriority(Integer.MAX_VALUE);

    private final int priority;

    public HandlingPriority(final int priority) {
        this.priority = priority;
    }

    public HandlingPriority increasePriority(final int priorityIncrease) {
        checkArgument(priorityIncrease > 0, "Negative increase");
        checkArgument(Long.valueOf(priority) + priorityIncrease < Integer.MAX_VALUE,
                "Resulting priority cannot be higher than %s", Integer.MAX_VALUE);
        return new HandlingPriority(priority + priorityIncrease);
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public int compareTo(final HandlingPriority o) {
        return Integer.compare(priority, o.priority);
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof HandlingPriority other && priority == other.priority;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(priority);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("priority", priority).toString();
    }
}
