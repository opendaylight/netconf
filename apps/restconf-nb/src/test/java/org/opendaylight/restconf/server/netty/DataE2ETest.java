/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opendaylight.restconf.api.MediaTypes.APPLICATION_YANG_DATA_JSON;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

class DataE2ETest extends AbstractE2ETest {

    @Test
    void userAuthenticationTest() {
        // TODO
    }

    @Test
    void crudOperationsTest() throws Exception {
        var result = invokeRequest(buildRequest(HttpMethod.POST, "/rests/data", APPLICATION_YANG_DATA_JSON, """
                <content>
                """));
        assertEquals(201, result.status().code());

        result = invokeRequest(buildRequest(HttpMethod.GET, "/rests/data",APPLICATION_YANG_DATA_JSON, null));
        assertEquals(200, result.status().code());

        result = invokeRequest(buildRequest(HttpMethod.PUT, "/rests/data", APPLICATION_YANG_DATA_JSON, """
                <content>
                """));
        assertEquals(201, result.status().code());

        result = invokeRequest(buildRequest(HttpMethod.DELETE, "/rests/data", APPLICATION_YANG_DATA_JSON, null));
        assertEquals(200, result.status().code());
    }

    @Test
    void errorHandlingTest() {
        // TODO
    }

    @Test
    void invokeActionTest() throws Exception {

    }

    @Test
    void dataOptionsTest() throws Exception {

    }

    @Test
    void dataHeadTest() throws Exception {

    }
}
