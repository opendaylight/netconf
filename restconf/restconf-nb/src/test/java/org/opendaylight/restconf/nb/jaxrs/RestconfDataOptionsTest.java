/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.query.ContentParam;

class RestconfDataOptionsTest extends AbstractRestconfTest {
    private final MultivaluedHashMap<String, String> queryParams = new MultivaluedHashMap<>();

    @BeforeEach
    void beforeEach() {
        doReturn(queryParams).when(uriInfo).getQueryParameters();
    }

    @Test
    void testRootOptions() {
        assertWritableRoot();
    }

    @Test
    void testRootAllOptions() {
        queryParams.add(ContentParam.uriName, ContentParam.ALL.paramValue());
        assertWritableRoot();
    }

    @Test
    void testRootConfigOptions() {
        queryParams.add(ContentParam.uriName, ContentParam.CONFIG.paramValue());
        assertWritableRoot();
    }

    @Test
    void testRootNonConfigOptions() {
        queryParams.add(ContentParam.uriName, ContentParam.NONCONFIG.paramValue());

        final var response = assertResponse(200, ar -> restconf.dataOPTIONS(uriInfo, sc, ar));
        assertEquals("GET, HEAD, OPTIONS", response.getHeaderString(HttpHeaders.ALLOW));
        assertNull(response.getHeaderString("Accept-Patch"));
    }

    @Test
    void testJukeboxDefault() {
        assertWritableJukebox();
    }

    @Test
    void testJukeboxAllDefault() {
        queryParams.add(ContentParam.uriName, ContentParam.ALL.paramValue());
        assertWritableJukebox();
    }

    @Test
    void testJukeboxConfigDefault() {
        queryParams.add(ContentParam.uriName, ContentParam.CONFIG.paramValue());
        assertWritableJukebox();
    }

    @Test
    void testJukeboxNonConfigOptions() {
        queryParams.add(ContentParam.uriName, ContentParam.NONCONFIG.paramValue());

        final var response = assertResponse(200, ar -> restconf.dataOPTIONS(JUKEBOX_API_PATH, uriInfo, sc, ar));
        assertEquals("GET, HEAD, OPTIONS", response.getHeaderString(HttpHeaders.ALLOW));
        assertNull(response.getHeaderString("Accept-Patch"));
    }

    private void assertWritableJukebox() {
        final var response = assertResponse(200, ar -> restconf.dataOPTIONS(JUKEBOX_API_PATH, uriInfo, sc, ar));
        assertEquals("DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT", response.getHeaderString(HttpHeaders.ALLOW));
        assertEquals("""
            application/yang-data+json, application/yang-data+xml, application/yang-patch+json, \
            application/yang-patch+xml, application/json, application/xml, text/xml""",
            response.getHeaderString("Accept-Patch"));
    }

    private void assertWritableRoot() {
        final var response = assertResponse(200, ar -> restconf.dataOPTIONS(uriInfo, sc, ar));
        assertEquals("GET, HEAD, OPTIONS, PATCH, POST, PUT", response.getHeaderString(HttpHeaders.ALLOW));
        assertEquals("""
            application/yang-data+json, application/yang-data+xml, application/yang-patch+json, \
            application/yang-patch+xml, application/json, application/xml, text/xml""",
            response.getHeaderString("Accept-Patch"));
    }

}
