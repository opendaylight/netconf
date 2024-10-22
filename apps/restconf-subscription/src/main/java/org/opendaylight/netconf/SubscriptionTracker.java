/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.yangtools.concepts.AbstractRegistration;

public class SubscriptionTracker {
    private final Map<TransportSession, List<Subscription>> sessionSubscriptions = new HashMap<>();
    // Start subscription ID generation from the upper half of uint32 (2,147,483,648)
    private static final long INITIAL_SUBSCRIPTION_ID = 2147483648L;
    private final AtomicLong subscriptionIdCounter = new AtomicLong(INITIAL_SUBSCRIPTION_ID);

    public boolean addSubscription(final TransportSession session) {
        if (sessionSubscriptions.containsKey(session)) {
            sessionSubscriptions.get(session).add(new Subscription(generateSubscriptionId()));
            return true; // probably ID?
        } else {
            sessionSubscriptions.put(session, new ArrayList<>()).add(new Subscription(generateSubscriptionId()));

            session.registerResource(new AbstractRegistration() {
                @Override
                protected void removeRegistration() {
                    removeSession(session);
                }
            });
        }

        return sessionSubscriptions.computeIfAbsent(session, s -> new ArrayList<>())
            .add(new Subscription(generateSubscriptionId()));
    }

    public void removeSession(final TransportSession session) {
        sessionSubscriptions.remove(session);
    }

    public List<Subscription> getSubscriptions(final TransportSession session) {
        return sessionSubscriptions.getOrDefault(session, Collections.emptyList());
    }

    /**
     * Generates a new subscription ID.
     * This method guarantees thread-safe, unique subscription IDs.
     *
     * @return A new subscription ID.
     */
    public long generateSubscriptionId() {
        return subscriptionIdCounter.getAndIncrement();
    }
}
