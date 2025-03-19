/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;

import java.util.function.Consumer;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.server.api.YangErrorsBody;

class NC1439Test extends AbstractRestconfTest {
    private static final MultivaluedMap<String, String> QUERY_PARAMS = new MultivaluedHashMap<>();

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void testPutWrongData() {
        doReturn(QUERY_PARAMS).when(uriInfo).getQueryParameters();
        final var result = assert400Error(ar -> restconf.dataJsonPUT(JUKEBOX_API_PATH, uriInfo, sc,
                stringInputStream("""
                {
                  "example-jukebox:jukebox" : {
                    "player": {
                      "WRONG": "0.2"
                    }
                  }
                }"""), ar));
        assertNotNull(result);
        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "Error parsing input: Schema node with name WRONG was not found under (http://example.com/ns/example-jukebox?revision=2015-04-04)player.",
                    "error-type": "protocol"
                  }
                ]
              }
            }""", result::formatToJSON, true);
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void testPatchDataWrongData() {
        final var result = assert400Error(ar -> restconf.dataJsonPATCH(JUKEBOX_API_PATH,
            stringInputStream("""
                {
                  "example-jukebox:jukebox" : {
                    "player": {
                      "WRONG": "0.2"
                    }
                  }
                }"""), sc, ar));
        assertNotNull(result);
        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "Error parsing input: Schema node with name WRONG was not found under (http://example.com/ns/example-jukebox?revision=2015-04-04)player.",
                    "error-type": "protocol"
                  }
                ]
              }
            }""", result::formatToJSON, true);
    }

    private static YangErrorsBody assert400Error(final Consumer<AsyncResponse> invocation) {
        return assertInstanceOf(YangErrorsBody.class, assertFormattableBody(400, invocation));
    }
}
