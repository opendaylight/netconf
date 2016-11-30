/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.services.impl;

import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.UriInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.base.services.impl.RestconfOperationsServiceImpl;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
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

    private static final List<String> listOfRpcsNames = new ArrayList<>();

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
        this.schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        this.schemaContextHandler = new SchemaContextHandler();
        this.schemaContextHandler.onGlobalContextUpdated(this.schemaContext);
        this.domMountPointServiceHandler = new DOMMountPointServiceHandler(this.domMountPointService);
        listOfRpcsNames.add("module2:dummy-rpc2-module2");
        listOfRpcsNames.add("module2:dummy-rpc1-module2");
        listOfRpcsNames.add("module1:dummy-rpc2-module1");
        listOfRpcsNames.add("module1:dummy-rpc1-module1");
    }

    @Test
    public void getOperationsTest() {
        final RestconfOperationsServiceImpl oper = new RestconfOperationsServiceImpl(this.schemaContextHandler, this.domMountPointServiceHandler);
        final NormalizedNodeContext operations = oper.getOperations(this.uriInfo);
        final ContainerNode data = (ContainerNode) operations.getData();
        Assert.assertTrue(
                data.getNodeType().getNamespace().toString().equals("urn:ietf:params:xml:ns:yang:ietf-restconf"));
        Assert.assertTrue(data.getNodeType().getLocalName().equals("operations"));
        for (final DataContainerChild<? extends PathArgument, ?> dataContainerChild : data.getValue()) {
            Assert.assertTrue(dataContainerChild.getNodeType().getNamespace().toString()
                    .equals("urn:ietf:params:xml:ns:yang:ietf-restconf"));
            Assert.assertTrue(listOfRpcsNames.contains(dataContainerChild.getNodeType().getLocalName()));
            Assert.assertTrue(dataContainerChild.getValue() == null);
        }

    }
}
