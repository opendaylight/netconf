/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import java.util.concurrent.atomic.AtomicLong;

public final class SubscriptionUtil {

    private SubscriptionUtil() {
        // hidden on purpose
    }

    /**
     * Generates a new subscription ID.
     * This method guarantees thread-safe, unique subscription IDs.
     *
     * @return A new subscription ID.
     */
    public static long generateSubscriptionId(final AtomicLong id) {
        return id.getAndIncrement();
    }
}
