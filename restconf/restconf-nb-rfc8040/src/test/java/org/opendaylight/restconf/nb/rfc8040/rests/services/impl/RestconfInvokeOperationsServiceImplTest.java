/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import java.util.Collections;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.RpcServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.references.SchemaContextRef;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class RestconfInvokeOperationsServiceImplTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/invoke-rpc";

    private RestconfInvokeOperationsServiceImpl invokeOperationsService;

    @Mock
    private RpcServiceHandler rpcServiceHandler;

    @Mock
    private DOMRpcService rpcService;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        final SchemaContextRef contextRef = new SchemaContextRef(
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT)));
        final TransactionChainHandler txHandler = mock(TransactionChainHandler.class);
        final DOMTransactionChain domTx = mock(DOMTransactionChain.class);
        when(txHandler.get()).thenReturn(domTx);
        final DOMDataTreeWriteTransaction wTx = mock(DOMDataTreeWriteTransaction.class);
        when(domTx.newWriteOnlyTransaction()).thenReturn(wTx);
        doReturn(CommitInfo.emptyFluentFuture()).when(wTx).commit();
        final SchemaContextHandler schemaContextHandler = SchemaContextHandler.newInstance(txHandler,
            mock(DOMSchemaService.class));
        schemaContextHandler.onGlobalContextUpdated(contextRef.get());
        this.invokeOperationsService =
                new RestconfInvokeOperationsServiceImpl(this.rpcServiceHandler, schemaContextHandler, txHandler);
        when(this.rpcServiceHandler.get()).thenReturn(this.rpcService);
    }

    @Test
    public void testInvokeRpcWithNonEmptyOutput() {
        final String identifier = "invoke-rpc-module:rpcTest";
        final ContainerNode result = mock(ContainerNode.class);
        final LeafNode outputChild = mock(LeafNode.class);
        when(result.getValue()).thenReturn(Collections.singleton(outputChild));

        final NormalizedNodeContext payload = prepNNC(result);
        final UriInfo uriInfo = mock(UriInfo.class);

        final NormalizedNodeContext rpc = this.invokeOperationsService.invokeRpc(identifier, payload, uriInfo);
        assertEquals(result, rpc.getData());
    }

    @Test
    public void testInvokeRpcWithEmptyOutput() {
        final String identifier = "invoke-rpc-module:rpcTest";
        final ContainerNode result = mock(ContainerNode.class);

        final NormalizedNodeContext payload = prepNNC(result);
        final UriInfo uriInfo = mock(UriInfo.class);

        WebApplicationException exceptionToBeThrown = null;
        try {
            this.invokeOperationsService.invokeRpc(identifier, payload, uriInfo);
        } catch (final WebApplicationException exception) {
            exceptionToBeThrown = exception;

        }
        Assert.assertNotNull("WebApplicationException with status code 204 is expected.", exceptionToBeThrown);
        Assert.assertEquals(Response.Status.NO_CONTENT.getStatusCode(), exceptionToBeThrown.getResponse().getStatus());
    }

    private NormalizedNodeContext prepNNC(final NormalizedNode<?, ?> result) {
        final InstanceIdentifierContext<?> context = mock(InstanceIdentifierContext.class);
        final RpcDefinition schemaNode = mock(RpcDefinition.class);
        final QName qname = QName.create("invoke:rpc:module", "2013-12-03", "rpcTest");
        final SchemaPath schemaPath = SchemaPath.create(true, qname);
        when(schemaNode.getPath()).thenReturn(schemaPath);
        when(schemaNode.getQName()).thenReturn(qname);
        doReturn(schemaNode).when(context).getSchemaNode();
        final NormalizedNode<?, ?> data = mock(NormalizedNode.class);
        final DOMRpcResult domRpcResult = mock(DOMRpcResult.class);
        when(this.rpcService.invokeRpc(schemaPath, data)).thenReturn(immediateFluentFuture(domRpcResult));
        doReturn(result).when(domRpcResult).getResult();
        return new NormalizedNodeContext(context, data);
    }
}
