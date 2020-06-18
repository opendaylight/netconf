/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import java.util.UUID;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * This interface defines a mechanism for generating unique numbers used by the Restconf server for
 * assigning identifiers to notification stream subscriptions.
 */
public interface SubscriptionIdGenerator {

    Uint32 nextId();

    /**
     * Sequential number generator. Starts with number 1.
     */
    final class Sequence implements SubscriptionIdGenerator {
        private Uint32 id = Uint32.valueOf(0);

        @Override
        public synchronized Uint32 nextId() {
            this.id = Uint32.valueOf(id.intValue() + 1);
            return id;
        }
    }

    /**
     * Random number generator. Generated numbers are unique.
     */
    final class Random implements SubscriptionIdGenerator {
        @Override
        public synchronized Uint32 nextId() {
            // subscription-id defined in yang model is uint32, therefore UUID.randomUUID().hashCode() &
            // Integer.MAX_VALUE; is required
            return Uint32.valueOf(Math.abs(UUID.randomUUID().hashCode() & Integer.MAX_VALUE));
        }
    }
}
