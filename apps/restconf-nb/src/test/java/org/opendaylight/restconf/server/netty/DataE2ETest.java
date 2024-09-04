/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

class DataE2ETest extends AbstractE2ETest {

    @Test
    void crudOperationsTest() throws Exception {

        // ensure topology branch exists
        var pretest = invokeRequest(
            buildRequest(HttpMethod.PUT, "/rests/data/network-topology:network-topology", APPLICATION_JSON,
                """
                    {
                        "network-topology:network-topology": {
                            "topology": [
                                {
                                    "topology-id": "topology-netconf"
                                }
                            ]
                        }
                    }"""));
        final var statusCode = pretest.status().code();
        assertTrue(statusCode == 200 || statusCode == 201);

        var result = invokeRequest(buildRequest(HttpMethod.POST,
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
                """));
        assertEquals(201, result.status().code());

        result = invokeRequest(buildRequest(HttpMethod.GET,
            "/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer2",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());

        // TODO validate content (created)

        result = invokeRequest(buildRequest(HttpMethod.PUT,
            "/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer2",
            APPLICATION_JSON,
            """
                {
                    "node": [{
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
                    }]
                }
                """));
        assertEquals(204, result.status().code());

        // TODO validate content (updated)

        result = invokeRequest(buildRequest(HttpMethod.DELETE,
            "/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer2",
            APPLICATION_JSON,
            null));
        assertEquals(204, result.status().code());
    }

    @Test
    void createErrorHandlingTest() throws Exception {
        var result = invokeRequest(buildRequest(HttpMethod.POST,
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
                """));
        assertEquals(201, result.status().code());

        result = invokeRequest(buildRequest(HttpMethod.POST,
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
                """));
        assertEquals(409, result.status().code());
    }

    @Test
    void readErrorHandlingTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.GET,
            "/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer2",
            APPLICATION_JSON,
            null));
        assertEquals(409, result.status().code());
    }

    @Test
    void updateErrorHandlingTest() throws Exception {
        var result = invokeRequest(buildRequest(HttpMethod.PUT,
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
                """));
        assertEquals(201, result.status().code());

        result = invokeRequest(buildRequest(HttpMethod.PUT,
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
                """));
        assertEquals(200, result.status().code());
    }

    @Test
    void deleteErrorHandlingTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.DELETE,
            "/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer2",
            APPLICATION_JSON,
            null));
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
    void invokePlainPatchTest() {
        // TODO
    }

    @Test
    void dataOptionsTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.OPTIONS,
            "rests/data",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());
    }

    @Test
    void dataHeadTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.HEAD,
            "rests/data",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());
    }
}
