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
    void test() throws Exception {
        final var read = invokeRequest(buildRequest(HttpMethod.GET,
            "/rests/data/network-topology:network-topology",
            APPLICATION_YANG_DATA_JSON,
            null));
        assertEquals(200, read.status().code());
    }

    @Test
    void crudOperationsTest() throws Exception {
        final var read = invokeRequest(buildRequest(HttpMethod.GET,
            "/rests/data",
            APPLICATION_YANG_DATA_JSON,
            null));
        assertEquals(200, read.status().code());

        var result = invokeRequest(buildRequest(HttpMethod.POST,
            "/rests/data/network-topology:network-topology/topology=topology-netconf",
            APPLICATION_YANG_DATA_JSON,
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
            APPLICATION_YANG_DATA_JSON,
            null));
        assertEquals(200, result.status().code());

        result = invokeRequest(buildRequest(HttpMethod.PUT,
            "/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer2",
            APPLICATION_YANG_DATA_JSON,
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

        result = invokeRequest(buildRequest(HttpMethod.DELETE,
            "/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer2",
            APPLICATION_YANG_DATA_JSON,
            null));
        assertEquals(204, result.status().code());
    }

    @Test
    void createErrorHandlingTest() throws Exception {
        var result = invokeRequest(buildRequest(HttpMethod.POST,
            "/rests/data/network-topology:network-topology/topology=topology-netconf",
            APPLICATION_YANG_DATA_JSON,
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
            APPLICATION_YANG_DATA_JSON,
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
            APPLICATION_YANG_DATA_JSON,
            null));
        assertEquals(409, result.status().code());
    }

    @Test
    void updateErrorHandlingTest() throws Exception {
        var result = invokeRequest(buildRequest(HttpMethod.PUT,
            "/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer2",
            APPLICATION_YANG_DATA_JSON,
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
            APPLICATION_YANG_DATA_JSON,
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
            APPLICATION_YANG_DATA_JSON,
            null));
        assertEquals(409, result.status().code());
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
