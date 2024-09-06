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

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

class OperationsE2ETest extends AbstractE2ETest {

    @Test
    void readAllOperationsTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.GET,
            "rests/operations",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());
    }

    @Test
    void readOperationTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.GET,
            "rests/operations/odl-device-notification:subscribe-device-notification",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());
    }

    @Test
    @SuppressWarnings("checkstyle:lineLength")
    void invokeOperationTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.POST,
            "rests/operations/odl-device-notification:subscribe-device-notification",
            APPLICATION_JSON,
            """
                {
                    "input": {
                        "path":"/network-topology:network-topology/topology[topology-id='topology-netconf']/node[node-id='test_device']"
                    }
                }"""));
        assertEquals(200, result.status().code());
    }

    // TODO shorten the payload or move it to file
    @Test
    void invokeCreateDeviceTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.POST,
            "rests/operations/netconf-node-topology:create-device",
            APPLICATION_JSON,
            """
                    {
                       "input": {
                         "login-password": {
                           "password": "Some password",
                           "username": "Some username"
                         },
                         "schema-cache-directory": "Some schema-cache-directory",
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
                     }"""));
        assertEquals(204, result.status().code());
    }

    @Test
    void invokeCreateDeviceDataMissingTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.POST,
            "rests/operations/netconf-node-topology:create-device-data-missing",
            APPLICATION_JSON,
            """
                    {
                       "input": {
                         "login-password": {
                           "password": "Some password",
                           "username": "Some username"
                         },
                         "schema-cache-directory": "Some schema-cache-directory",
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
                     }"""));
        assertEquals(409, result.status().code());
    }

    @Test
    void invokeCreateDeviceNotFoundTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.POST,
            "rests/operations/netconf-node-topology:create-device",
            APPLICATION_JSON,
            """
                    {
                       "input": {
                         "login-password-not-found": {
                           "password": "Some password",
                           "username": "Some username"
                         },
                         "schema-cache-directory": "Some schema-cache-directory",
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
                     }"""));
        assertEquals(500, result.status().code());
    }

    @Test
    void invokeCreateDeviceMalformedMessageTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.POST,
            "rests/operations/netconf-node-topology:create-device",
            APPLICATION_JSON,
            """
                    {
                       "input": {
                         "login-password": {
                           "password": "Some password",
                           "username": "Some username"
                         },
                         "schema-cache-directory": "Some schema-cache-directory",
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
                     }"""));
        assertEquals(500, result.status().code());
    }

    @Test
    void errorHandlingTest() {
        // TODO wrong accept type
        // not authorized?
        // not implemented
        // data-missing?
    }

    @Test
    void operationsOptionsTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.OPTIONS,
            "rests/operations",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());
    }

    @Test
    void operationsHeadTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.HEAD,
            "rests/operations",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());
    }
}
