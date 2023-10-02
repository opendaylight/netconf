/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.operations;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;

public final class HandlingPriority implements Comparable<HandlingPriority> {
    // FIXME: remote this constant
    public static final HandlingPriority CANNOT_HANDLE = new HandlingPriority(null);
    public static final HandlingPriority HANDLE_WITH_DEFAULT_PRIORITY = new HandlingPriority(Integer.MIN_VALUE);
    public static final HandlingPriority HANDLE_WITH_MAX_PRIORITY = new HandlingPriority(Integer.MAX_VALUE);

    private final Integer priority;

    private HandlingPriority(final Integer priority) {
        this.priority = priority;
    }

    public static @NonNull HandlingPriority of(final int priority) {
        return new HandlingPriority(priority);
    }

    /**
     * Get priority number.
     *
     * @return priority number or Optional.absent otherwise
     */
    public Optional<Integer> getPriority() {
        return Optional.ofNullable(priority);
    }

    public HandlingPriority increasePriority(final int priorityIncrease) {
        checkState(priority != null, "Unable to increase priority for %s", this);
        checkArgument(priorityIncrease > 0, "Negative increase");
        checkArgument(Long.valueOf(priority) + priorityIncrease < Integer.MAX_VALUE,
                "Resulting priority cannot be higher than %s", Integer.MAX_VALUE);
        return of(priority + priorityIncrease);
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public int compareTo(final HandlingPriority o) {
        if (priority == null) {
            return o.priority == null ? 0 : -1;
        }
        return o.priority == null ? 1 : Integer.compare(priority, o.priority);
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof HandlingPriority other && Objects.equals(priority, other.priority);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(priority) ;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("priority", priority).toString();
    }
}
