/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

class StreamsE2ETest extends AbstractE2ETest {
    // TODO make it one test and save uuid of create test

    @Test
    void createStreamTest() throws Exception {
        var result = invokeRequest(HttpMethod.POST,
            "rests/operations/sal-remote:create-data-change-event-subscription",
            APPLICATION_JSON,
            """
                {
                    "input": {
                        "path": "/toaster:toaster/toaster:toasterStatus",
                        "sal-remote-augment:datastore": "OPERATIONAL",
                        "sal-remote-augment:scope": "ONE"
                    }
                }
                """);
        assertEquals(201, result.status().code());
    }

    @Test
    void readAllStreamsTest() throws Exception {
        var result = invokeRequest(HttpMethod.GET, "rests/data/ietf-restconf-monitoring:restconf-state/streams");
        assertEquals(201, result.status().code());
        // todo validate content
    }

    @Test
    void readStreamTest() throws Exception {
        final var uuid = "b3db417c-0305-473d-b6c8-2da01c543171";
        var result = invokeRequest(HttpMethod.GET,
            "rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=urn:uuid:" + uuid,
            APPLICATION_JSON);
        assertEquals(201, result.status().code());
    }

    @Test
    void subscribeToStreamTest() throws Exception {
        final var uuid = "b3db417c-0305-473d-b6c8-2da01c543171";
        var result = invokeRequest(HttpMethod.GET,
            "/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=urn:uuid:" + uuid,
            APPLICATION_JSON);
        assertEquals(201, result.status().code());
    }
}
