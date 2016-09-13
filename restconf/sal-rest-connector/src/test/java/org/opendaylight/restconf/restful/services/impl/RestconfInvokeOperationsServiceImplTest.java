/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Iterator;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;

public class RestconfInvokeOperationsServiceImplTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/streams";

    @Test
    public void testInvokeRpc() throws Exception {
        final SchemaContextRef contextRef = new SchemaContextRef(TestRestconfUtils.loadSchemaContext(PATH_FOR_NEW_SCHEMA_CONTEXT));
        final SchemaContextHandler schemaContextHandler = new SchemaContextHandler();
        schemaContextHandler.onGlobalContextUpdated(contextRef.get());
        final RestconfInvokeOperationsServiceImpl invokeOperationsService = new RestconfInvokeOperationsServiceImpl(null, schemaContextHandler);

        final QName qname = QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", "toasterStatus");
        final QName qname1 = QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", "toaster");
        final QName rpcQnameInput = QName.create("urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "2014-01-14", "input");
        final QName inputQname = QName.create(rpcQnameInput, "path");
        final YangInstanceIdentifier iid = YangInstanceIdentifier.builder()
                .node(rpcQnameInput)
                .build();
        final YangInstanceIdentifier iidAsLeafValue = YangInstanceIdentifier.builder()
                .node(qname1)
                .node(qname)
                .build();

        final LeafNode contentLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(inputQname))
                .withValue(iidAsLeafValue)
                .build();
        final ContainerNode input = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(rpcQnameInput))
                .withChild(contentLeaf)
                .build();
        final Iterator<RpcDefinition> iterator = contextRef.get().getOperations().iterator();
        RpcDefinition rpcDef = null;
        while (iterator.hasNext()) {
            rpcDef = iterator.next();
            if ("create-data-change-event-subscription".equals(rpcDef.getQName().getLocalName())) {
                break;
            }
        }

        final InstanceIdentifierContext<RpcDefinition> iidContext = new InstanceIdentifierContext<>(iid, rpcDef, null, contextRef.get());
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, input);
        final NormalizedNodeContext context = invokeOperationsService.invokeRpc(null, payload, null);

        final QName rpcQnameOutput = QName.create("urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "2014-01-14", "output");
        final QName outputQname = QName.create("urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "2014-01-14", "stream-name");
        final LeafNode contentLeaf2 = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(outputQname))
                .withValue("toaster:toaster/toasterStatus/datastore=CONFIGURATION/scope=BASE")
                .build();
        final ContainerNode output = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(rpcQnameOutput))
                .withChild(contentLeaf2)
                .build();
        final InstanceIdentifierContext<RpcDefinition> iidContextResult = new InstanceIdentifierContext<>(null, rpcDef, null, contextRef.get());
        final NormalizedNodeContext payloadResult = new NormalizedNodeContext(iidContextResult, output);

        assertNotNull(context);
        assertEquals(payloadResult.getData(), context.getData());
        assertEquals(payloadResult.getInstanceIdentifierContext().getSchemaNode(),
                context.getInstanceIdentifierContext().getSchemaNode());
        assertEquals(payloadResult.getInstanceIdentifierContext().getSchemaContext(),
                context.getInstanceIdentifierContext().getSchemaContext());
        assertNull(context.getInstanceIdentifierContext().getMountPoint());
        assertNull(context.getInstanceIdentifierContext().getInstanceIdentifier());
    }

}
