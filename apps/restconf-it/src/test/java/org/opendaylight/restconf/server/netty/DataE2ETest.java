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

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataE2ETest extends AbstractE2ETest {
    private static final String TOPOLOGY_URI =
        "/rests/data/network-topology:network-topology/topology=topology-netconf";
    private static final String TOPOLOGY_NODE_URI = TOPOLOGY_URI + "/node=netopeer2";

    @BeforeEach
    @Override
    void beforeEach() throws Exception {
        super.beforeEach();
        resetTopologyNode();
    }

    @Test
    void dataCRUD() throws Exception {
        // create
        final var initialNode = """
            {
                "network-topology:node": [
                    {
                        "node-id": "netopeer2",
                        "netconf-node-topology:login-password-unencrypted": {
                            "password": "admin",
                            "username": "admin"
                        },
                        "netconf-node-topology:host": "172.17.0.2",
                        "netconf-node-topology:port": 830,
                        "netconf-node-topology:tcp-only": false,
                        "netconf-node-topology:protocol": {
                            "name": "SSH"
                        }
                    }
                ]
            }""";
        var response = invokeRequest(HttpMethod.POST, TOPOLOGY_URI, APPLICATION_JSON, initialNode);
        assertEquals(HttpResponseStatus.CREATED, response.status());

        // read (validate created)
        assertJsonContent(TOPOLOGY_NODE_URI, initialNode);

        // update (merge)
        response = invokeRequest(HttpMethod.PATCH, TOPOLOGY_NODE_URI, APPLICATION_JSON,
            """
                {
                    "network-topology:node": [
                        {
                            "node-id": "netopeer2",
                            "netconf-node-topology:port": 831
                        }
                    ]
                }
                """);
        assertEquals(HttpResponseStatus.OK, response.status());

        // validate updated
        assertJsonContent(TOPOLOGY_NODE_URI, """
            {
                "network-topology:node": [
                    {
                        "node-id": "netopeer2",
                        "netconf-node-topology:login-password-unencrypted": {
                            "password": "admin",
                            "username": "admin"
                        },
                        "netconf-node-topology:host": "172.17.0.2",
                        "netconf-node-topology:port": 831,
                        "netconf-node-topology:tcp-only": false,
                        "netconf-node-topology:protocol": {
                            "name": "SSH"
                        }
                    }
                ]
            }
            """);

        // replace
        final var replaceNode = """
            {
                "network-topology:node": [{
                    "node-id": "netopeer2",
                    "netconf-node-topology:login-password-unencrypted": {
                        "password": "admin",
                        "username": "admin"
                    },
                    "netconf-node-topology:host": "172.17.0.2",
                    "netconf-node-topology:port": 6513,
                    "netconf-node-topology:tcp-only": false,
                    "netconf-node-topology:protocol": {
                        "name": "TLS"
                    }
                }]
            }
            """;
        response = invokeRequest(HttpMethod.PUT, TOPOLOGY_NODE_URI, APPLICATION_JSON, replaceNode);
        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());

        // validate replaced
        assertJsonContent(TOPOLOGY_NODE_URI, replaceNode);

        // delete
        response = invokeRequest(HttpMethod.DELETE, TOPOLOGY_NODE_URI);
        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
    }

    @Test
    void createErrorHandlingTest() throws Exception {
        var result = invokeRequest(HttpMethod.POST,
            "/rests/data/network-topology:network-topology/topology=topology-netconf",
            APPLICATION_JSON,
            """
                {
                    "network-topology:node": [
                        {
                            "node-id": "netopeer2",
                            "netconf-node-topology:login-password-unencrypted": {
                                "netconf-node-topology:password": "admin",
                                "netconf-node-topology:username": "admin"
                            },
                            "netconf-node-topology:host": "172.17.0.2",
                            "netconf-node-topology:port": 830,
                            "netconf-node-topology:tcp-only": false,
                            "protocol": {
                                "name": "SSH"
                            }
                        }
                    ]
                }
                """);
        assertEquals(201, result.status().code());

        result = invokeRequest(HttpMethod.POST,
            "/rests/data/network-topology:network-topology/topology=topology-netconf",
            APPLICATION_JSON,
            """
                {
                    "node": [
                        {
                            "node-id": "netopeer2",
                            "netconf-node-topology:login-password-unencrypted": {
                                "netconf-node-topology:password": "admin",
                                "netconf-node-topology:username": "admin"
                            },
                            "netconf-node-topology:host": "172.17.0.2",
                            "netconf-node-topology:port": 830,
                            "netconf-node-topology:tcp-only": false,
                            "protocol": {
                                "name": "SSH"
                            }
                        }
                    ]
                }
                """);
        assertEquals(409, result.status().code());
    }

    @Test
    void readErrorHandlingTest() throws Exception {
        final var result = invokeRequest(HttpMethod.GET,
            "/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer2",
            APPLICATION_JSON);
        assertEquals(409, result.status().code());
    }

    @Test
    void updateErrorHandlingTest() throws Exception {
        var result = invokeRequest(HttpMethod.PUT,
            "/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer2",
            APPLICATION_JSON,
            """
                {
                    "node": [
                        {
                            "node-id": "netopeer2",
                            "netconf-node-topology:login-password-unencrypted": {
                                "netconf-node-topology:password": "admin",
                                "netconf-node-topology:username": "admin"
                            },
                            "netconf-node-topology:host": "172.17.0.2",
                            "netconf-node-topology:port": 6513,
                            "netconf-node-topology:tcp-only": false,
                            "protocol": {
                                "name": "TLS"
                            }
                        }
                    ]
                }
                """);
        assertEquals(201, result.status().code());

        result = invokeRequest(HttpMethod.PUT,
            "/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer2",
            APPLICATION_JSON,
            """
                {
                    "node": [
                        {
                            "node-id": "netopeer2",
                            "netconf-node-topology:login-password-unencrypted": {
                                "netconf-node-topology:password": "admin",
                                "netconf-node-topology:username": "admin"
                            },
                            "netconf-node-topology:host": "172.17.0.2",
                            "netconf-node-topology:port": 6513,
                            "netconf-node-topology:tcp-only": false,
                            "protocol": {
                                "name": "TLS"
                            }
                        }
                    ]
                }
                """);
        assertEquals(200, result.status().code());
    }

    @Test
    void deleteErrorHandlingTest() throws Exception {
        final var result = invokeRequest(HttpMethod.DELETE,
            "/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer2",
            APPLICATION_JSON);
        assertEquals(409, result.status().code());
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
    void dataOptionsTest() throws Exception {
        final var result = invokeRequest(HttpMethod.OPTIONS, "/rests/data");
        assertEquals(200, result.status().code());
    }

    @Test
    void dataHeadTest() throws Exception {
        final var result = invokeRequest(HttpMethod.HEAD, "/rests/data");
        assertEquals(200, result.status().code());
    }

    protected void resetTopologyNode() throws Exception {
        // ensure topology node exists
        // insert or replace with default value
        var response = invokeRequest(HttpMethod.PUT,
            "/rests/data/network-topology:network-topology",
            APPLICATION_JSON,
            """
                {
                    "network-topology:network-topology": {
                        "topology": [
                            {
                                "topology-id": "topology-netconf",
                                "node": []
                            }
                        ]
                    }
                }""");
        final var statusCode = response.status().code();
        assertTrue(statusCode == 200 || statusCode == 201);
    }
}
