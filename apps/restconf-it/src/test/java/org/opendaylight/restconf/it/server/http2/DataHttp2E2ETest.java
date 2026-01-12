/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

class DataHttp2E2ETest extends AbstractHttp2E2ETest {
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
    protected void beforeEach() throws Exception {
        super.beforeEach();
        resetDataNode();
    }

    @Test
    void dataCRUDJsonTest() throws Exception {
        // create
        var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(PARENT_URI))
            .POST(HttpRequest.BodyPublishers.ofString(INITIAL_NODE_JSON))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.CREATED.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());

        // read (validate created)
        assertContentJson(ITEM_URI, INITIAL_NODE_JSON);

        // update (merge)
        final var body =
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
                }""";
        response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(ITEM_URI))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());

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

        response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(ITEM_URI))
            .PUT(HttpRequest.BodyPublishers.ofString(replaceNode))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.NO_CONTENT.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());

        // validate replaced
        assertContentJson(ITEM_URI, replaceNode);

        // delete
        response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(ITEM_URI))
            .DELETE()
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.NO_CONTENT.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());

        // validate deleted
        response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(ITEM_URI))
            .GET()
            .header(HttpHeaderNames.ACCEPT.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

    @Test
    void dataExistsErrorJsonTest() throws Exception {
        // insert data first time
        var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(PARENT_URI))
            .POST(HttpRequest.BodyPublishers.ofString(INITIAL_NODE_JSON))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertEquals(HttpResponseStatus.CREATED.code(), response.statusCode());

        // subsequent insert of same node
        response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(PARENT_URI))
            .POST(HttpRequest.BodyPublishers.ofString(INITIAL_NODE_JSON))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS);
    }

    @Test
    void dataMissingErrorJsonTest() throws Exception {
        // GET case
        var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(ITEM_URI))
            .GET()
            .header(HttpHeaderNames.ACCEPT.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);

        // DELETE case
        response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(ITEM_URI))
            .DELETE()
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

    @Test
    void invokeActionTest() throws Exception {
        // invoke action
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(ACTIONS_URI + "/example-action"))
            .POST(HttpRequest.BodyPublishers.ofString(ACTION_INPUT_JSON))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
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
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(ACTIONS_URI + "/example-action"))
            .POST(HttpRequest.BodyPublishers.ofString(
                """
                    {
                        "input": {
                            "wrong-data": "Some wrong data"
                        }
                    }"""))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), response.statusCode());
        assertEquals("Schema node with name wrong-data was not found under (example:action?revision=2024-09-19)input.",
            response.body());
    }

    @Test
    void invokeNotImplementedActionTest() throws Exception {
        // invoke not implemented action
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(ACTIONS_URI + "/not-implemented"))
            .POST(HttpRequest.BodyPublishers.ofString(ACTION_INPUT_JSON))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertErrorResponseJson(response, ErrorType.RPC, ErrorTag.OPERATION_FAILED);
    }

    @Test
    void yangPatchTest() throws Exception {
        // CRUD
        final var body = """
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
            }""";

        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(ITEM_URI))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .header(HttpHeaderNames.ACCEPT.toString(), MediaTypes.APPLICATION_YANG_DATA_JSON)
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), MediaTypes.APPLICATION_YANG_PATCH_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());

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
        var body = """
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
            }""";
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(ITEM_URI))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .header(HttpHeaderNames.ACCEPT.toString(), MediaTypes.APPLICATION_YANG_DATA_JSON)
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), MediaTypes.APPLICATION_YANG_PATCH_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
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
        var body = """
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
            }""";
        var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(ITEM_URI))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .header(HttpHeaderNames.ACCEPT.toString(), MediaTypes.APPLICATION_YANG_DATA_JSON)
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), MediaTypes.APPLICATION_YANG_PATCH_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
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
        var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(DATA_URI))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertOptionsResponse(response, Set.of("GET", "POST", "PUT", "PATCH", "OPTIONS", "HEAD"));
        assertHeaderValue(response, HttpHeaderNames.ACCEPT_PATCH.toString(), patchAcceptTypes);

        // non-root also deletable
        response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(PARENT_URI))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertOptionsResponse(response, Set.of("GET", "POST", "PUT", "PATCH", "OPTIONS", "HEAD", "DELETE"));
        assertHeaderValue(response, HttpHeaderNames.ACCEPT_PATCH.toString(), patchAcceptTypes);
    }

    @Test
    void dataHeadTest() throws Exception {
        assertHead(DATA_URI);
    }

    private void resetDataNode() throws Exception {
        // ensure data node exists before we insert a child node
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri("/rests/data/example-jukebox:jukebox"))
            .PUT(HttpRequest.BodyPublishers.ofString("""
                {
                    "example-jukebox:jukebox": {
                        "library": {}
                    }
                }"""))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
        final var status = response.statusCode();
        assertTrue(status == HttpResponseStatus.OK.code() || status ==  HttpResponseStatus.CREATED.code());
    }
}
