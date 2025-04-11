/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

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
    private static final String ACTIONS_URI = DATA_URI + "/example-action:root";
    private static final String ITEM_URI = PARENT_URI + "/artist=art%2Fist";
    private static final String INITIAL_NODE_JSON = """
        {
            "example-jukebox:artist": [
                {
                    "name": "art/ist",
                    "album": [{
                        "name": "album1",
                        "genre": "example-jukebox:rock",
                        "year": 2020
                    }]
                }
            ]
        }""";
    private static final String ACTION_INPUT_JSON = """
        {
            "input": {
                "data": "Some data"
            }
        }""";

    @BeforeEach
    @Override
    void beforeEach() throws Exception {
        super.beforeEach();
        resetDataNode();
    }

    @Test
    void dataCRUDJsonTest() throws Exception {
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
                            "name": "art/ist",
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
                            "name": "art/ist",
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
                            "name": "art/ist",
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
    void dataExistsErrorJsonTest() throws Exception {
        // insert data first time
        var response = invokeRequest(HttpMethod.POST, PARENT_URI, APPLICATION_JSON, INITIAL_NODE_JSON);
        assertEquals(HttpResponseStatus.CREATED, response.status());
        // subsequent insert of same node
        response = invokeRequest(HttpMethod.POST, PARENT_URI, APPLICATION_JSON, INITIAL_NODE_JSON);
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS);
    }

    @Test
    void dataMissingErrorJsonTest() throws Exception {
        // GET case
        var response = invokeRequest(HttpMethod.GET, ITEM_URI);
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
        // DELETE case
        response = invokeRequest(HttpMethod.DELETE, ITEM_URI);
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

    @Test
    void invokeActionTest() throws Exception {
        // invoke action
        final var response = invokeRequest(HttpMethod.POST, ACTIONS_URI + "/example-action", APPLICATION_JSON,
            ACTION_INPUT_JSON);
        assertContentJson(response, """
            {
                "example-action:output": {
                    "response":"Action was invoked"
                }
            }""");
    }

    @Test
    void invokeActionWithBadInputsTest() throws Exception {
        // invoke action
        final var response = invokeRequest(HttpMethod.POST, ACTIONS_URI + "/example-action", APPLICATION_JSON,
            """
            {
                "input": {
                    "wrong-data": "Some wrong data"
                }
            }""");
        assertSimpleErrorResponse(response,"Schema node with name wrong-data was not found under "
            + "(example:action?revision=2024-09-19)input.", HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void invokeNotImplementedActionTest() throws Exception {
        // invoke not implemented action
        final var response = invokeRequest(HttpMethod.POST, ACTIONS_URI + "/not-implemented", APPLICATION_JSON,
            ACTION_INPUT_JSON);
        assertErrorResponseJson(response, ErrorType.RPC, ErrorTag.OPERATION_FAILED);
    }

    @Test
    void yangPatchTest() throws Exception {
        // CRUD
        var response = invokeRequest(HttpMethod.PATCH, ITEM_URI, MediaTypes.APPLICATION_YANG_PATCH_JSON,
            MediaTypes.APPLICATION_YANG_DATA_JSON, """
            {
                "ietf-yang-patch:yang-patch" : {
                    "patch-id" : "patch1",
                    "edit" : [
                        {
                            "edit-id": "edit1",
                            "operation": "create",
                            "target": "/album=album1",
                            "value": {
                                "album": {
                                    "name": "album1",
                                    "genre": "example-jukebox:rock",
                                    "year": 2020
                                }
                            }
                        },
                        {
                            "edit-id": "edit2",
                            "operation": "create",
                            "target": "/album=album2",
                            "value": {
                                "album": {
                                    "name": "album2",
                                    "genre": "example-jukebox:jazz",
                                    "year": 2020
                                }
                            }
                        },
                        {
                            "edit-id": "edit3",
                            "operation": "replace",
                            "target": "/album=album1",
                            "value": {
                                "album": {
                                    "name": "album1",
                                    "genre": "example-jukebox:pop",
                                    "year": 2024
                                }
                            }
                        },
                        {
                            "edit-id": "edit4",
                            "operation": "delete",
                            "target": "/album=album2"
                        }
                    ]
                }
            }""");
        assertEquals(HttpResponseStatus.OK, response.status());

        // read (validate result)
        assertContentJson(ITEM_URI, """
            {
                "example-jukebox:artist": [
                    {
                        "name": "art/ist",
                        "album": [
                            {
                                "name": "album1",
                                "genre": "example-jukebox:pop",
                                "year": 2024
                            }
                        ]
                    }
                ]
            }""");
    }

    @Test
    void yangPatchDataMissingErrorTest() throws Exception {
        // One correct edit, one - not
        var response = invokeRequest(HttpMethod.PATCH, ITEM_URI, MediaTypes.APPLICATION_YANG_PATCH_JSON,
            MediaTypes.APPLICATION_YANG_DATA_JSON, """
            {
                "ietf-yang-patch:yang-patch" : {
                    "patch-id" : "patch1",
                    "edit" : [
                        {
                            "edit-id": "edit1",
                            "operation": "create",
                            "target": "/album=album1",
                            "value": {
                                "album": {
                                    "name": "album1",
                                    "genre": "example-jukebox:rock",
                                    "year": 2020
                                }
                            }
                        },
                        {
                            "edit-id": "edit2",
                            "operation": "delete",
                            "target": "/album=album2"
                        }
                    ]
                }
            }""");
        assertContentJson(response, """
            {
                "ietf-yang-patch:yang-patch-status": {
                    "patch-id":"patch1",
                    "edit-status": {
                        "edit": [
                        {
                            "edit-id":"edit1",
                            "ok":[null]
                        },
                        {
                            "edit-id":"edit2",
                            "errors": {
                                "error": [
                                {
                                    "error-type":"protocol",
                                    "error-tag":"data-missing",
                                    "error-path":
                                    "/example-jukebox:jukebox/library/artist[name='art/ist']/album[name='album2']",
                                    "error-message":"Data does not exist"
                                }]
                            }
                        }]
                    }
                }
            }""");
    }

    @Test
    void yangPatchDataExistsErrorTest() throws Exception {
        // One correct edit, one - not
        var response = invokeRequest(HttpMethod.PATCH, ITEM_URI, MediaTypes.APPLICATION_YANG_PATCH_JSON,
            MediaTypes.APPLICATION_YANG_DATA_JSON, """
            {
                "ietf-yang-patch:yang-patch" : {
                    "patch-id" : "patch1",
                    "edit" : [
                        {
                            "edit-id": "edit1",
                            "operation": "create",
                            "target": "/album=album1",
                            "value": {
                                "album": {
                                    "name": "album1",
                                    "genre": "example-jukebox:rock",
                                    "year": 2020
                                }
                            }
                        },
                        {
                            "edit-id": "edit2",
                            "operation": "create",
                            "target": "/album=album1",
                            "value": {
                                "album": {
                                    "name": "album1",
                                    "genre": "example-jukebox:jazz",
                                    "year": 2020
                                }
                            }
                        }
                    ]
                }
            }""");
        assertContentJson(response, """
            {
                "ietf-yang-patch:yang-patch-status": {
                    "patch-id":"patch1",
                    "edit-status": {
                        "edit": [
                        {
                            "edit-id":"edit1",
                            "ok":[null]
                        },
                        {
                            "edit-id":"edit2",
                            "errors": {
                                "error": [
                                {
                                    "error-type":"protocol",
                                    "error-tag":"data-exists",
                                    "error-path":
                                    "/example-jukebox:jukebox/library/artist[name='art/ist']/album[name='album1']",
                                    "error-message":"Data already exists"
                                }]
                            }
                        }]
                    }
                }
            }""");
    }

    @Test
    void dataOptionsTest() throws Exception {
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
    void dataHeadTest() throws Exception {
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
