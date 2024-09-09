/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;

class RestconfOperationsOptionsTest extends AbstractRestconfTest {
    private final MultivaluedHashMap<String, String> queryParams = new MultivaluedHashMap<>();

    @Test
    void testOperationsOptions() {
        final var response = restconf.operationsOPTIONS();
        assertEquals(200, response.getStatus());
        assertEquals("GET, HEAD, OPTIONS", response.getHeaderString(HttpHeaders.ALLOW));
    }

    @Test
    void testJukeboxDefault() {
        doReturn(queryParams).when(uriInfo).getQueryParameters();
        final var response = assertResponse(200, ar -> restconf.operationsOPTIONS(apiPath("example-jukebox:play"),
            uriInfo, sc, ar));
        assertEquals("GET, HEAD, OPTIONS", response.getHeaderString(HttpHeaders.ALLOW));
    }
}
