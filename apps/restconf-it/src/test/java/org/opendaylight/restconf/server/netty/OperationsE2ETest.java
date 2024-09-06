/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.eos.dom.simple.SimpleDOMEntityOwnershipService;
import org.opendaylight.mdsal.singleton.impl.EOSClusterSingletonServiceProvider;
import org.opendaylight.netconf.keystore.legacy.impl.DefaultNetconfKeystoreService;

class OperationsE2ETest extends AbstractE2ETest {
    private static final String OPERATIONS_URI = "/rests/operations";
    private static final String SUBSCRIBE_DEVICE_NOTIFICATIONS_URI =
        OPERATIONS_URI + "/odl-device-notification:subscribe-device-notification";
    private static final String WRONG_TYPE = "application/svg+xml";
    private static final String CREATE_DEVICE = "rests/operations/netconf-node-topology:create-device";

    private DefaultNetconfKeystoreService netconfKeystoreService;

    @BeforeEach
    @Override
    void beforeEach() throws Exception {
        super.beforeEach();
        final var cssService = new EOSClusterSingletonServiceProvider(new SimpleDOMEntityOwnershipService());
        netconfKeystoreService = new DefaultNetconfKeystoreService(getDataBroker(), rpcProviderService,
            cssService, new TestEncryptionService());
    }

    @AfterEach
    @Override
    void afterEach() {
        netconfKeystoreService.close();
        netconfKeystoreService = null;
        super.afterEach();
    }

    @Test
    void readOperationsJson() throws Exception {
        // check those we'll use in tests
        assertContentJson(OPERATIONS_URI, """
            {
                "ietf-restconf:operations" : {
                    "netconf-keystore:add-keystore-entry" : [null],
                    "netconf-keystore:remove-keystore-entry" : [null],
                    "netconf-keystore:add-private-key" : [null],
                    "netconf-keystore:remove-private-key" : [null],
                    "netconf-keystore:add-trusted-certificate" : [null],
                    "netconf-keystore:remove-trusted-certificate" : [null],
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
    void createKeystoreEntry() throws Exception {
        var response = invokeRequest(HttpMethod.POST,
            "/rests/operations/netconf-keystore:add-keystore-entry",
            APPLICATION_XML,
            """
                <input xmlns="urn:opendaylight:netconf:keystore">
                    <key-credential>
                        <key-id>key-id</key-id>
                        <private-key>-----BEGIN RSA PRIVATE KEY-----
                MIIEpgIBAAKCAQEAvM2whCdDEmxTfio1oUCmxlZBvkyircTGraSjCeZWA5gdXcFg
                R3Po49/obQLXONwVE8ULP+wm9vFVae5AdXRjndG/xxi/Z/HAZqkXMXvjRIYiBVgD
                cu3c0jRdbimwS84zKYIygXGeHODvNKQcMNFNENQz2f/QIVPRioFB4sHxynvP4re0
                +pYUNsl6gpZFtMZ804oqWAuk90Db0x1jgn87FLh3FXAOLHqmWDDjoj6jLo4FMLNo
                ydstJQ14Nm3Gsg8LWoV9qGT/hYpfYnzuQizb/ZsPs3vEcV3g67RRCc82X5jKeXJo
                /C5r3czrM68X3TKBtvIMThmF17p79/nuLyt9kQIDAQABAoIBAQCOWLoj+QINqtSM
                Q8CpcfgLg08P7fGc98YfdwhhV2M0VISXgktXs+E7pT40qjagLPZLMH2Z1S9PcYbH
                VhUNORI+E7z2nAb7lH5OKGBPM6uWp1aRFtmK1iFt7oMeopnDnZRfUEVJ6OKfvUs8
                Mhr7B2KGNKdfTgqahfpu5aNKFpV45fGaPFhIeKJeTFExVQNmcHwxzQPqeocOHuEF
                XaOzLidXM9KT9gGpsf0Fo0rv6K9oZGO3C5GRnf2jkWL0PpoJLWjR7fNGWZp1nsGa
                oG8m9wjb6KYkQwrrvcVmzjPcmwvfcG0FBUMLwMNKiJ5/nispbR0TpL7nt01fG2P1
                rwyunbVtAoGBAO8RjNHJt7FFOi82xKcJNe6SwD6p8TRJNW9j1MxhOS/yaq9DDyVR
                pwFHZo7pK/TU4HBc6DYyFN9W5+4Tn/cWmuu9YfimhNquoFO531qF3ltJ6ZY+wRs3
                cYzQouC7uww68PdLGGE2vxJLXuvVrmsbevh3+VKtLdqXO8v618KCc+3/AoGBAMos
                zgJ0Rjxm+VObvYjJz+fzaLCNNORJO4C47F9woy4inSUOO2P30QEBRC+UPjM5AJiz
                Up8X1NCOW43o3zfAmBhtDLIwhrW8fWc/OQeqDt7GM9Q2a8yyXPACqDqsBDxTeu3M
                hEwACzXuHRscQyIRV8HHCWph3xwTwLGG9ZIEcLRvAoGBAIbsNbh0ispuUo8w7r2C
                skBp7Duxd6LVqmWqRv/t4vOPcexmAVdDhOhw3o3LRPaRafWgSaHElAkUKCMySjaO
                OHLRWEiX2iT9Jxj5rveM09hbl4wm8J8mpFwfp70D1mXpofM/G4xJ9H4jsXeSCjUC
                tl0igMDLYjSa47GUaU6qhzkLAoGBAMID9T7NrolQmHvvvReD9Ay3vgOPvu5EiOGi
                lNOSGEax2PQykDQDIYNBX9n4/SfS0Au6KtOZ3xS1SI8Kpwutu0fVfpWRk/TbicyH
                E4eTXunScvJ3t0Oc9yssoZyMbxQlWJbT6TG16Qw8EZpuqM4Mrpa7FwIMIjujiQvU
                Y91YfX/pAoGBANtGPuVorE8U7PmAfTvEUKwvpDUz2QWZCOYOazBMrXNVsWLSdaaI
                6ZQosKgQ9ThVYwItqE1dBp5vjPQKwjBGW2EbCHl2XUJXh9cH87P3kyYAc9sqeQGf
                +dSOkNEWsmFl1VwaCUt6L+jqhfBGQQK1bAHmjdRfsaj1gZyE9OoHfvk2
                -----END RSA PRIVATE KEY-----</private-key>
                        <passphrase></passphrase>
                    </key-credential>
                </input>""");
        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        // TODO validate result in datastore
    }

    @Test
    @Disabled
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
    @Disabled
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
    @Disabled
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
    @Disabled
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
    @Disabled
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
