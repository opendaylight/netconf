/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.TestUtils;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonNormalizedNodeBodyWriter;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfOperationsServiceTest {
    private Set<QName> listOfRpcsNames;
    private RestconfOperationsServiceImpl oper;

    @Before
    public void init() throws Exception {
        oper = new RestconfOperationsServiceImpl(TestUtils.newSchemaContextHandler(
            YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/modules"))),
            mock(DOMMountPointService.class));

        final QNameModule module1 = QNameModule.create(XMLNamespace.of("module:1"));
        final QNameModule module2 = QNameModule.create(XMLNamespace.of("module:2"));
        listOfRpcsNames = Set.of(
            QName.create(module1, "dummy-rpc1-module1"), QName.create(module1, "dummy-rpc2-module1"),
            QName.create(module2, "dummy-rpc1-module2"), QName.create(module2, "dummy-rpc2-module2"));
    }

    @Test
    public void getOperationsTest() throws IOException {
        final NormalizedNodeContext operations = oper.getOperations(mock(UriInfo.class));
        final ContainerNode data = (ContainerNode) operations.getData();
        assertEquals("urn:ietf:params:xml:ns:yang:ietf-restconf",
            data.getIdentifier().getNodeType().getNamespace().toString());
        assertEquals("operations", data.getIdentifier().getNodeType().getLocalName());

        assertEquals(4, data.body().size());

        for (final DataContainerChild child : data.body()) {
            assertEquals(Empty.getInstance(), child.body());

            final QName qname = child.getIdentifier().getNodeType().withoutRevision();
            assertTrue(listOfRpcsNames.contains(qname));
        }

        // FIXME: add XML validation
        assertEquals("{\"ietf-restconf:operations\":{"
            + "\"module1:dummy-rpc1-module1\":[null],"
            + "\"module2:dummy-rpc1-module2\":[null],"
            + "\"module2:dummy-rpc2-module2\":[null],"
            + "\"module1:dummy-rpc2-module1\":[null]}}", toJson(operations));
    }

    private static String toJson(final NormalizedNodeContext operations) throws IOException {
        final var bos = new ByteArrayOutputStream();
        new JsonNormalizedNodeBodyWriter().writeTo(operations, null, null, null, null, null, bos);
        return new String(bos.toString(StandardCharsets.UTF_8));
    }
}
