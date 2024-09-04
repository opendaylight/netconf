/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

class DataE2ETest extends AbstractE2ETest {
    private static final String DATA_URI = "/rests/data";
    private static final String PARENT_URI = DATA_URI + "/example-jukebox:jukebox/library";
    private static final String ITEM_URI = PARENT_URI + "/artist=artist";
    private static final String INITIAL_NODE_JSON = """
        {
            "example-jukebox:artist": [
                {
                    "name": "artist",
                    "album": [{
                        "name": "album1",
                        "genre": "example-jukebox:rock",
                        "year": 2020
                    }]
                }
            ]
        }""";

    @BeforeEach
    @Override
    void beforeEach() throws Exception {
        super.beforeEach();
        resetDataNode();
    }

    @Test
    void dataCRUDJson() throws Exception {
        // create
        var response = invokeRequest(HttpMethod.POST, PARENT_URI, APPLICATION_JSON, INITIAL_NODE_JSON);
        assertEquals(HttpResponseStatus.CREATED, response.status());

        // read (validate created)
        assertContentJson(ITEM_URI, INITIAL_NODE_JSON);

        // update (merge)
        response = invokeRequest(HttpMethod.PATCH, ITEM_URI, APPLICATION_JSON,
            """
                {
                    "example-jukebox:artist": [
                        {
                            "name": "artist",
                            "album": [{
                                "name": "album1",
                                "genre": "example-jukebox:jazz"
                            }]
                        }
                    ]
                }""");
        assertEquals(HttpResponseStatus.OK, response.status());

        // validate updated
        assertContentJson(ITEM_URI, """
                {
                    "example-jukebox:artist": [
                        {
                            "name": "artist",
                            "album": [{
                                "name": "album1",
                                "genre": "example-jukebox:jazz",
                                "year": 2020
                            }]
                        }
                    ]
                }""");

        // replace
        final var replaceNode = """
                {
                    "example-jukebox:artist": [
                        {
                            "name": "artist",
                            "album": [{
                                "name": "album2",
                                "genre": "example-jukebox:pop",
                                "year": 2024
                            }]
                        }
                    ]
                }""";
        response = invokeRequest(HttpMethod.PUT, ITEM_URI, APPLICATION_JSON, replaceNode);
        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());

        // validate replaced
        assertContentJson(ITEM_URI, replaceNode);

        // delete
        response = invokeRequest(HttpMethod.DELETE, ITEM_URI);
        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());

        // validate deleted
        response = invokeRequest(HttpMethod.GET, ITEM_URI);
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

    @Test
    void dataExistsErrorJson() throws Exception {
        // insert data first time
        var response = invokeRequest(HttpMethod.POST, PARENT_URI, APPLICATION_JSON, INITIAL_NODE_JSON);
        assertEquals(HttpResponseStatus.CREATED, response.status());
        // subsequent insert of same node
        response = invokeRequest(HttpMethod.POST, PARENT_URI, APPLICATION_JSON, INITIAL_NODE_JSON);
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS);
    }

    @Test
    void dataMissingErrorJson() throws Exception {
        // GET case
        var response = invokeRequest(HttpMethod.GET, ITEM_URI);
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
        // DELETE case
        response = invokeRequest(HttpMethod.DELETE, ITEM_URI);
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

    @Test
    void invokeActionTest() throws Exception {
        // TODO
    }

    @Test
    void invokeYangPatchTest() {
        // TODO
    }

    @Test
    void dataOptions() throws Exception {
        final var patchAcceptTypes =  Set.of("text/xml", APPLICATION_JSON, APPLICATION_XML,
            MediaTypes.APPLICATION_YANG_DATA_JSON, MediaTypes.APPLICATION_YANG_DATA_XML,
            MediaTypes.APPLICATION_YANG_PATCH_JSON, MediaTypes.APPLICATION_YANG_PATCH_XML);
        // root
        var response = invokeRequest(HttpMethod.OPTIONS, DATA_URI);
        assertOptionsResponse(response, Set.of("GET", "POST", "PUT", "PATCH", "OPTIONS", "HEAD"));
        assertHeaderValue(response, HttpHeaderNames.ACCEPT_PATCH, patchAcceptTypes);

        // non-root also deletable
        response = invokeRequest(HttpMethod.OPTIONS, PARENT_URI);
        assertOptionsResponse(response, Set.of("GET", "POST", "PUT", "PATCH", "OPTIONS", "HEAD", "DELETE"));
        assertHeaderValue(response, HttpHeaderNames.ACCEPT_PATCH, patchAcceptTypes);
    }

    @Test
    void dataHead() throws Exception {
        assertHead(DATA_URI);
    }

    private void resetDataNode() throws Exception {
        // ensure data node exists before we insert a child node
        var response = invokeRequest(HttpMethod.PUT,
            "/rests/data/example-jukebox:jukebox",
            APPLICATION_JSON,
            """
                {
                    "example-jukebox:jukebox": {
                        "library": {}
                    }
                }""");
        final var status = response.status().code();
        assertTrue(status == HttpResponseStatus.OK.code() || status ==  HttpResponseStatus.CREATED.code());
    }
}
