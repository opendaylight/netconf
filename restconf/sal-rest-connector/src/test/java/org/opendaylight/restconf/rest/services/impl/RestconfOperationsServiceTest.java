/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map.Entry;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class RestconfOperationsServiceTest {

    @Mock
    private DOMMountPointService domMountPointService;

    @Mock
    private UriInfo uriInfo;

    private SchemaContext schemaContext;
    private SchemaContextHandler schemaContextHandler;
    private DOMMountPointServiceHandler domMountPointServiceHandler;

    private List<Entry<String, String>> listOfRpcsNames;

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
        this.schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        this.schemaContextHandler = new SchemaContextHandler();
        this.schemaContextHandler.onGlobalContextUpdated(this.schemaContext);
        this.domMountPointServiceHandler = new DOMMountPointServiceHandler(this.domMountPointService);

        listOfRpcsNames = ImmutableList.of(
            new SimpleEntry<>("module:1", "dummy-rpc1-module1"),
            new SimpleEntry<>("module:1", "dummy-rpc2-module1"),
            new SimpleEntry<>("module:2", "dummy-rpc1-module2"),
            new SimpleEntry<>("module:2", "dummy-rpc2-module2"));
    }

    @Test
    public void getOperationsTest() {
        final RestconfOperationsServiceImpl oper = new RestconfOperationsServiceImpl(this.schemaContextHandler,
            this.domMountPointServiceHandler);
        final NormalizedNodeContext operations = oper.getOperations(this.uriInfo);
        final ContainerNode data = (ContainerNode) operations.getData();
        assertEquals("urn:ietf:params:xml:ns:yang:ietf-restconf", data.getNodeType().getNamespace().toString());
        assertEquals("operations", data.getNodeType().getLocalName());

        assertEquals(4, data.getValue().size());

        for (final DataContainerChild<? extends PathArgument, ?> child : data.getValue()) {
            assertNull(child.getValue());

            final QName type = child.getNodeType();
            assertTrue(listOfRpcsNames.contains(new SimpleEntry<>(type.getNamespace().toString(), type.getLocalName())));
        }
    }
}
