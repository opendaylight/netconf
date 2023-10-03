/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.operations;

import static com.google.common.base.Preconditions.checkArgument;

import org.eclipse.jdt.annotation.NonNull;

public record HandlingPriority(int priority) implements Comparable<HandlingPriority> {
    public static final @NonNull HandlingPriority HANDLE_WITH_DEFAULT_PRIORITY =
        new HandlingPriority(Integer.MIN_VALUE);
    public static final @NonNull HandlingPriority HANDLE_WITH_MAX_PRIORITY = new HandlingPriority(Integer.MAX_VALUE);

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
}
