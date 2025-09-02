/*
 * Copyright (c) 2025 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opendaylight.restconf.server.TestUtils.buildRequest;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

class PipeliningTest extends AbstractRequestProcessorTest {

    @Test
    void testPipeliningQueue() {
        mockExecutor();
        final var request = buildRequest(HttpMethod.GET, DATA_PATH, TestUtils.TestEncoding.JSON, null);

        // Dispatch all requests manually
        manualRequestDispatch(request);
        manualRequestDispatch(request);
        manualRequestDispatch(request);

        // At this point, all requests are queued
        assertEquals(3, blockedRequestsSize());

        // Manually finish each request to simulate pipeline processing
        manualRequestFinish();
        assertEquals(2, blockedRequestsSize());
        manualRequestFinish();
        assertEquals(1, blockedRequestsSize());
        manualRequestFinish();
        assertEquals(0, blockedRequestsSize());
    }

}
