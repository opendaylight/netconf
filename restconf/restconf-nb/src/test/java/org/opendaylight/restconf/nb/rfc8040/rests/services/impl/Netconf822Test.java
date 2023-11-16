/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@ExtendWith(MockitoExtension.class)
class Netconf822Test {
    private static final @NonNull DatabindContext DATABIND =
        DatabindContext.ofModel(YangParserTestUtils.parseYangResourceDirectory("/nc822"));

    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMMountPointService mountPointService;

    private MdsalRestconfServer server;

    @BeforeEach
    void beforeEach() {
        server = new MdsalRestconfServer(() -> DATABIND, dataBroker, rpcService, mountPointService);
    }

    @Test
    void testOperationsContent() {
        final var content = server.operationsGET();
        assertEquals("""
            {
              "ietf-restconf:operations" : {
                "foo:new": [null],
                "foo:new1": [null]
              }
            }""", content.toJSON());
        assertEquals("""
            <?xml version="1.0" encoding="UTF-8"?>
            <operations xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf"
                        xmlns:ns0="foo">
              <ns0:new/>
              <ns0:new1/>
            </operations>""", content.toXML());
    }

    @Test
    void testOperationsContentByIdentifier() {
        final var content = server.operationsGET("foo:new1");
        assertNotNull(content);
        assertEquals("""
            {
              "ietf-restconf:operations" : {
                "foo:new1": [null]
              }
            }""", content.toJSON());
        assertEquals("""
            <?xml version="1.0" encoding="UTF-8"?>
            <operations xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf"
                        xmlns:ns0="foo">
              <ns0:new1/>
            </operations>""", content.toXML());
    }
}
