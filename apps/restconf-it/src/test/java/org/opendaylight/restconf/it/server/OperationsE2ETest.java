/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.eos.dom.simple.SimpleDOMEntityOwnershipService;
import org.opendaylight.mdsal.singleton.impl.EOSClusterSingletonServiceProvider;
import org.opendaylight.netconf.keystore.legacy.impl.DefaultNetconfKeystoreService;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

class OperationsE2ETest extends AbstractE2ETest {
    private static final String OPERATIONS_URI = "/rests/operations";
    private static final String SUBSCRIBE_DEVICE_NOTIFICATIONS_URI =
        OPERATIONS_URI + "/odl-device-notification:subscribe-device-notification";
    private static final String WRONG_TYPE = "application/svg+xml";
    private static final String ADD_KEYSTORE_ENTRY = "/rests/operations/netconf-keystore:add-keystore-entry";

    private DefaultNetconfKeystoreService netconfKeystoreService;

    @BeforeEach
    @Override
    void beforeEach() throws Exception {
        super.beforeEach();
        final var cssService = new EOSClusterSingletonServiceProvider(new SimpleDOMEntityOwnershipService());
        netconfKeystoreService = new DefaultNetconfKeystoreService(getDataBroker(), rpcProviderService,
            cssService, new NullAAAEncryptionService());
    }

    @AfterEach
    @Override
    void afterEach() throws Exception {
        netconfKeystoreService.close();
        netconfKeystoreService = null;
        super.afterEach();
    }

    @Test
    void readOperationsJsonTest() throws Exception {
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
    void readSingleOperationJsonTest() throws Exception {
        assertContentJson(SUBSCRIBE_DEVICE_NOTIFICATIONS_URI, """
            {
                "odl-device-notification:subscribe-device-notification": [null],
            }""");
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void createKeystoreEntryTest() throws Exception {
        final var response = invokeRequest(HttpMethod.POST, ADD_KEYSTORE_ENTRY,
            APPLICATION_XML, """
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

        // validate content
        assertContentXml("/rests/data/netconf-keystore:keystore/key-credential=key-id", """
            <key-credential xmlns="urn:opendaylight:netconf:keystore">
                <key-id>key-id</key-id>
                <algorithm>RSA</algorithm>
                <public-key>MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvM2whCdDEmxTfio1oUCmxlZBvkyircTGraSjCeZWA5gdXcFgR3Po49/obQLXONwVE8ULP+wm9vFVae5AdXRjndG/xxi/Z/HAZqkXMXvjRIYiBVgDcu3c0jRdbimwS84zKYIygXGeHODvNKQcMNFNENQz2f/QIVPRioFB4sHxynvP4re0+pYUNsl6gpZFtMZ804oqWAuk90Db0x1jgn87FLh3FXAOLHqmWDDjoj6jLo4FMLNoydstJQ14Nm3Gsg8LWoV9qGT/hYpfYnzuQizb/ZsPs3vEcV3g67RRCc82X5jKeXJo/C5r3czrM68X3TKBtvIMThmF17p79/nuLyt9kQIDAQAB</public-key>
                <private-key>MIIEwAIBADANBgkqhkiG9w0BAQEFAASCBKowggSmAgEAAoIBAQC8zbCEJ0MSbFN+KjWhQKbGVkG+TKKtxMatpKMJ5lYDmB1dwWBHc+jj3+htAtc43BUTxQs/7Cb28VVp7kB1dGOd0b/HGL9n8cBmqRcxe+NEhiIFWANy7dzSNF1uKbBLzjMpgjKBcZ4c4O80pBww0U0Q1DPZ/9AhU9GKgUHiwfHKe8/it7T6lhQ2yXqClkW0xnzTiipYC6T3QNvTHWOCfzsUuHcVcA4seqZYMOOiPqMujgUws2jJ2y0lDXg2bcayDwtahX2oZP+Fil9ifO5CLNv9mw+ze8RxXeDrtFEJzzZfmMp5cmj8LmvdzOszrxfdMoG28gxOGYXXunv3+e4vK32RAgMBAAECggEBAI5YuiP5Ag2q1IxDwKlx+AuDTw/t8Zz3xh93CGFXYzRUhJeCS1ez4TulPjSqNqAs9kswfZnVL09xhsdWFQ05Ej4TvPacBvuUfk4oYE8zq5anVpEW2YrWIW3ugx6imcOdlF9QRUno4p+9SzwyGvsHYoY0p19OCpqF+m7lo0oWlXjl8Zo8WEh4ol5MUTFVA2ZwfDHNA+p6hw4e4QVdo7MuJ1cz0pP2Aamx/QWjSu/or2hkY7cLkZGd/aORYvQ+mgktaNHt80ZZmnWewZqgbyb3CNvopiRDCuu9xWbOM9ybC99wbQUFQwvAw0qInn+eKyltHROkvue3TV8bY/WvDK6dtW0CgYEA7xGM0cm3sUU6LzbEpwk17pLAPqnxNEk1b2PUzGE5L/Jqr0MPJVGnAUdmjukr9NTgcFzoNjIU31bn7hOf9xaa671h+KaE2q6gU7nfWoXeW0nplj7BGzdxjNCi4Lu7DDrw90sYYTa/Ekte69Wuaxt6+Hf5Uq0t2pc7y/rXwoJz7f8CgYEAyizOAnRGPGb5U5u9iMnP5/NosI005Ek7gLjsX3CjLiKdJQ47Y/fRAQFEL5Q+MzkAmLNSnxfU0I5bjejfN8CYGG0MsjCGtbx9Zz85B6oO3sYz1DZrzLJc8AKoOqwEPFN67cyETAALNe4dGxxDIhFXwccJamHfHBPAsYb1kgRwtG8CgYEAhuw1uHSKym5SjzDuvYKyQGnsO7F3otWqZapG/+3i849x7GYBV0OE6HDejctE9pFp9aBJocSUCRQoIzJKNo44ctFYSJfaJP0nGPmu94zT2FuXjCbwnyakXB+nvQPWZemh8z8bjEn0fiOxd5IKNQK2XSKAwMtiNJrjsZRpTqqHOQsCgYEAwgP1Ps2uiVCYe++9F4P0DLe+A4++7kSI4aKU05IYRrHY9DKQNAMhg0Ff2fj9J9LQC7oq05nfFLVIjwqnC627R9V+lZGT9NuJzIcTh5Ne6dJy8ne3Q5z3KyyhnIxvFCVYltPpMbXpDDwRmm6ozgyulrsXAgwiO6OJC9Rj3Vh9f+kCgYEA20Y+5WisTxTs+YB9O8RQrC+kNTPZBZkI5g5rMEytc1WxYtJ1pojplCiwqBD1OFVjAi2oTV0Gnm+M9ArCMEZbYRsIeXZdQleH1wfzs/eTJgBz2yp5AZ/51I6Q0RayYWXVXBoJS3ov6OqF8EZBArVsAeaN1F+xqPWBnIT06gd++TY=</private-key>
            </key-credential>
            """);
    }

    @Test
    void invalidOperationErrorXMLTest() throws Exception {
        final var response = invokeRequest(HttpMethod.POST,
            "/rests/operations/netconf-keystore:invalid-operation",
            APPLICATION_XML, """
                <input xmlns="urn:opendaylight:netconf:keystore" />""");
        assertErrorResponseXml(response, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

    @Test
    void missingDataErrorXmlTest() throws Exception {
        final var response = invokeRequest(HttpMethod.POST,
            ADD_KEYSTORE_ENTRY,
            APPLICATION_XML,
            """
                <input xmlns="urn:opendaylight:netconf:keystore">
                    <key-credential>
                        <key-id>key-id</key-id>
                        <passphrase></passphrase>
                    </key-credential>
                </input>""");
        assertErrorResponseXml(response, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
    }

    @Test
    void invalidDataErrorXmlTest() throws Exception {
        final var response = invokeRequest(HttpMethod.POST,
            ADD_KEYSTORE_ENTRY,
            APPLICATION_XML,
            """
                <input xmlns="urn:opendaylight:netconf:keystore">
                    <key-credential>
                        <key-id>key-id</key-id>
                        <private-key>bad-content</private-key>
                        <passphrase></passphrase>
                    </key-credential>
                </input>""");
        assertErrorResponseXml(response, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
    }

    @Test
    void invalidMediaTypeTest() throws Exception {
        final var response = invokeRequest(HttpMethod.POST, ADD_KEYSTORE_ENTRY, WRONG_TYPE,"""
                <input xmlns="urn:opendaylight:netconf:keystore" />""");
        assertEquals(HttpResponseStatus.NOT_ACCEPTABLE, response.status());
    }

    @Test
    void unimplementedRpcErrorTest() throws Exception {
        final var response = invokeRequest(HttpMethod.POST,
            "/rests/operations/example-jukebox:play",
            APPLICATION_JSON,
            """
                {
                    "input": {
                        "playlist": "playlist name",
                        "song-number": 1
                    }
                }""");
        assertErrorResponseJson(response, ErrorType.RPC, ErrorTag.OPERATION_FAILED);
    }

    @Test
    void operationsOptionsTest() throws Exception {
        assertOptions(OPERATIONS_URI, Set.of("GET", "HEAD", "OPTIONS"));
        assertOptions(SUBSCRIBE_DEVICE_NOTIFICATIONS_URI, Set.of("GET", "HEAD", "OPTIONS", "POST"));
    }

    @Test
    void operationsHeadTest() throws Exception {
        assertHead(OPERATIONS_URI);
    }
}
