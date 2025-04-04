/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

public enum SubscriptionState {
    /**
     * Default state assigned to the subscription upon creation. Should be changed to active state after successful
     * subscription.
     */
    START,
    /**
     * State assigned upon successful subscription or after suspended state is lifted.
     */
    ACTIVE,
    /**
     * State assigned by the publisher when there is no sufficient CPU or bandwidth available to service the
     * subscription.
     */
    SUSPENDED,
    /**
     * State assigned upon termination of subscription.
     */
    END
}
