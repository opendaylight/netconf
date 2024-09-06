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
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class OperationsE2ETest extends AbstractE2ETest {
    private static final String OPERATIONS_URI = "/rests/operations";
    private static final String SUBSCRIBE_DEVICE_NOTIFICATIONS_URI =
        OPERATIONS_URI + "/odl-device-notification:subscribe-device-notification";
    private static final String WRONG_TYPE = "application/svg+xml";
    private static final String CREATE_DEVICE = "rests/operations/netconf-node-topology:create-device";

    @Test
    void readOperationsJson() throws Exception {
        // check those we'll use in tests
        assertContentJson(OPERATIONS_URI, """
            {
                "ietf-restconf:operations" : {
                    "netconf-node-topology:create-device": [null],
                    "netconf-node-topology:delete-device": [null],
                    "odl-device-notification:subscribe-device-notification": [null],
                    "sal-remote:create-data-change-event-subscription": [null],
                    "sal-remote:create-notification-stream": [null]
                }
            }""");
    }

    @Test
    void readSingleOperationJson() throws Exception {
        assertContentJson(SUBSCRIBE_DEVICE_NOTIFICATIONS_URI, """
            {
                "odl-device-notification:subscribe-device-notification": [null],
            }""");
    }

    @Test
    @Disabled()
    // TODO currently replies error of no RPC implementation found
    //  seems this rpc requires topology with device up
    void invokeOperationTest() throws Exception {
        // preset topology node data
        var response = invokeRequest(HttpMethod.PUT,
            "/rests/data/network-topology:network-topology",
            APPLICATION_JSON,
            """
                {
                    "network-topology:network-topology": {
                        "topology": [
                            {
                                "topology-id": "test",
                                "node": [
                                    {
                                        "node-id": "test",
                                        "netconf-node-topology:login-password-unencrypted": {
                                            "password": "admin",
                                            "username": "admin"
                                        }
                                    }
                                ]
                            }
                        ]
                    }
                }""");
        final var status = response.status();
        assertTrue(status == HttpResponseStatus.OK || status == HttpResponseStatus.CREATED);

        response = invokeRequest(HttpMethod.POST,
            "/rests/operations/odl-device-notification:subscribe-device-notification",
            APPLICATION_JSON,
            """
                {
                    "input": {
                        "path":"/network-topology:network-topology/topology[topology-id='test']/node[node-id='test']"
                    }
                }""");
        assertEquals(200, response.status().code());
    }

    @Test
    void invokeCreateDeviceTest() throws Exception {
        final var result = invokeRequest(HttpMethod.POST,
            CREATE_DEVICE,
            APPLICATION_JSON,
            """
                {
                   "input": {
                     "login-password": {
                       "password": "Some password",
                       "username": "Some username"
                     },
                     "host": "0.0.0.0",
                     "port": 0,
                     "tcp-only": true,
                     "protocol": {
                       "name": "SSH"
                     },
                     "schemaless": true,
                     "reconnect-on-changed-schema": true,
                     "node-id": "Some node-id"
                   }
                }""");
        assertEquals(204, result.status().code());
    }

    @Test
    void invokeCreateDeviceDataMissingTest() throws Exception {
        final var result = invokeRequest(HttpMethod.POST,
            CREATE_DEVICE + "data-missing",
            APPLICATION_JSON,
            """
                {
                   "input": {
                     "login-password": {
                       "password": "Some password",
                       "username": "Some username"
                     },
                     "host": "0.0.0.0",
                     "port": 0,
                     "tcp-only": true,
                     "protocol": {
                       "name": "SSH"
                     },
                     "schemaless": true,
                     "reconnect-on-changed-schema": true,
                     "node-id": "Some node-id"
                   }
                }""");
        assertEquals(409, result.status().code());
    }

    @Test
    void invokeCreateDeviceNotFoundTest() throws Exception {
        final var result = invokeRequest(HttpMethod.POST,
            CREATE_DEVICE,
            APPLICATION_JSON,
            """
                {
                   "input": {
                     "login-password-not-found": {
                       "password": "Some password",
                       "username": "Some username"
                     },
                     "host": "0.0.0.0",
                     "port": 0,
                     "tcp-only": true,
                     "protocol": {
                       "name": "SSH"
                     },
                     "schemaless": true,
                     "reconnect-on-changed-schema": true,
                     "node-id": "Some node-id"
                   }
                }""");
        assertEquals(500, result.status().code());
    }

    @Test
    void invokeCreateDeviceMalformedMessageTest() throws Exception {
        final var result = invokeRequest(HttpMethod.POST,
            CREATE_DEVICE,
            APPLICATION_JSON,
            """
                {
                   "input": {
                     "login-password": {
                       "password": "Some password",
                       "username": "Some username"
                     },
                     "host": "0.0.0.0",
                     "port": "abc",
                     "tcp-only": true,
                     "protocol": {
                       "name": "SSH"
                     },
                     "schemaless": true,
                     "reconnect-on-changed-schema": true,
                     "node-id": "Some node-id"
                   }
                }""");
        assertEquals(500, result.status().code());
    }

    @Test
    void invokeCreateDeviceWrongAcceptTypeTest() throws Exception {
        final var result = invokeRequest(HttpMethod.POST,
            CREATE_DEVICE,
            WRONG_TYPE,
            """
                {
                   "input": {
                     "login-password": {
                       "password": "Some password",
                       "username": "Some username"
                     },
                     "host": "0.0.0.0",
                     "port": "abc",
                     "tcp-only": true,
                     "protocol": {
                       "name": "SSH"
                     },
                     "schemaless": true,
                     "reconnect-on-changed-schema": true,
                     "node-id": "Some node-id"
                   }
                }""");
        assertEquals(406, result.status().code());
    }

    @Test
    void operationsOptions() throws Exception {
        // FIXME to be fixed via https://lf-opendaylight.atlassian.net/browse/NETCONF-1210
        // assertOptions(OPERATIONS_URI, Set.of("GET", "HEAD", "OPTIONS"));
        assertOptions(SUBSCRIBE_DEVICE_NOTIFICATIONS_URI, Set.of("GET", "HEAD", "OPTIONS", "POST"));
    }

    @Test
    void operationsHead() throws Exception {
        assertHead(OPERATIONS_URI);
    }
}
