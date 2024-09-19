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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.binding.api.ActionSpec;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.yang.gen.v1.example.action.rev240919.Root;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleAction;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleActionInput;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleActionOutput;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleActionOutputBuilder;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

class DataE2ETest extends AbstractE2ETest {
    private static final String DATA_URI = "/rests/data";
    private static final String PARENT_URI = DATA_URI + "/example-jukebox:jukebox/library";
    private static final String ACTIONS_URI = DATA_URI + "/example-action:root";
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
    private static final String ACTIONS_NODE_JSON = """
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
        actionProviderService.registerImplementation(ActionSpec.builder(Root.class).build(ExampleAction.class),
            new ExampleActionImpl());
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
        // invoke action
        final var response = invokeRequest(HttpMethod.POST, ACTIONS_URI + "/example-action", APPLICATION_JSON,
            ACTIONS_NODE_JSON);
        assertContentJson(response, """
            {
                "example-action:output": {
                    "response":"Action was invoked"
                }
            }""");
    }

    @Test
    void invokeNotImplementedActionTest() throws Exception {
        // invoke not implemented action
        final var response = invokeRequest(HttpMethod.POST, ACTIONS_URI + "/not-implemented", APPLICATION_JSON,
            ACTIONS_NODE_JSON);
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, response.status());
    }

    @Test
    void invokeBadDataActionTest() throws Exception {
        // invoke action
        final var response = invokeRequest(HttpMethod.POST, ACTIONS_URI + "/example-action", APPLICATION_JSON,
            """
            {
                "input": {
                    "wrong-data": "Some wrong data"
                }
            }""");
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, response.status());
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

    static final class ExampleActionImpl implements ExampleAction {
        @Override
        public ListenableFuture<RpcResult<ExampleActionOutput>> invoke(DataObjectIdentifier<Root> path,
                ExampleActionInput input) {
            return Futures.immediateFuture(RpcResultBuilder.success(
                new ExampleActionOutputBuilder().setResponse("Action was invoked").build()).build());
        }
    }
}
