/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

class SubscriptionUtil {
    static SubscriptionState moveState(final SubscriptionState from, final SubscriptionState to) {
        return switch (from) {
            case START, SUSPENDED -> switch (to) {
                case START, SUSPENDED -> throw new IllegalStateException("Cannot transition from %s to %s"
                        .formatted(from, to));
                case ACTIVE, END -> to;
            };
            case ACTIVE -> switch (to) {
                case START, ACTIVE -> throw new IllegalStateException("Cannot transition from %s to %s".formatted(from,
                        to));
                case SUSPENDED, END -> to;
            };
            case END -> throw new IllegalStateException("Cannot transition from %s to %s".formatted(from, to));
        };
    }
}
