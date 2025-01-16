/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

public class NotificationSubscriptionTest extends AbstractNotificationSubscriptionTest {
    /**
     * Tests successful establish subscription RPC.
     */
    @Test
    void establishSubscriptionTest() throws Exception {
        final var response = invokeRequest(HttpMethod.GET, "/restconf/data/ietf-subscribed-notifications:streams");
        assertNotNull(response);
        assertNotNull(response.content());
    }
}
