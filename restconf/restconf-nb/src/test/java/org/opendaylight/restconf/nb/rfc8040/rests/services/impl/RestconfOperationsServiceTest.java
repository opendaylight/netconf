/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfOperationsServiceTest {
    @Test
    public void getOperationsTest() throws IOException {
        final var context = YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/modules"));
        final var oper = new RestconfOperationsServiceImpl(() -> DatabindContext.ofModel(context));

        assertEquals("{\n"
            + "  \"ietf-restconf:operations\" : {\n"
            + "    \"module1:dummy-rpc1-module1\": [null],\n"
            + "    \"module1:dummy-rpc2-module1\": [null],\n"
            + "    \"module2:dummy-rpc1-module2\": [null],\n"
            + "    \"module2:dummy-rpc2-module2\": [null]\n"
            + "  }\n"
            + "}", oper.getOperationsJSON());
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<operations xmlns=\"urn:ietf:params:xml:ns:yang:ietf-restconf\"\n"
            + "            xmlns:ns0=\"module:1\"\n"
            + "            xmlns:ns1=\"module:2\" >\n"
            + "  <ns0:dummy-rpc1-module1/>\n"
            + "  <ns0:dummy-rpc2-module1/>\n"
            + "  <ns1:dummy-rpc1-module2/>\n"
            + "  <ns1:dummy-rpc2-module2/>\n"
            + "</operations>", oper.getOperationsXML());
    }
}
