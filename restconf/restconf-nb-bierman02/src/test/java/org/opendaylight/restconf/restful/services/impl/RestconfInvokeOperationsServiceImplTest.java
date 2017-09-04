/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.services.impl;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import javax.ws.rs.core.UriInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.handlers.RpcServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.handlers.TransactionChainHandler;
import org.opendaylight.yangtools.yang.common.QName;
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
                YangParserTestUtils.parseYangSources(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT)));
        final TransactionChainHandler txHandler = Mockito.mock(TransactionChainHandler.class);
        final DOMTransactionChain domTx = Mockito.mock(DOMTransactionChain.class);
        Mockito.when(txHandler.get()).thenReturn(domTx);
        final DOMDataWriteTransaction wTx = Mockito.mock(DOMDataWriteTransaction.class);
        Mockito.when(domTx.newWriteOnlyTransaction()).thenReturn(wTx);
        final CheckedFuture<Void, TransactionCommitFailedException> checked = Mockito.mock(CheckedFuture.class);
        Mockito.when(wTx.submit()).thenReturn(checked);
        Mockito.when(checked.checkedGet()).thenReturn(null);
        final SchemaContextHandler schemaContextHandler = new SchemaContextHandler(txHandler);
        schemaContextHandler.onGlobalContextUpdated(contextRef.get());
        this.invokeOperationsService =
                new RestconfInvokeOperationsServiceImpl(this.rpcServiceHandler, schemaContextHandler);
        Mockito.when(this.rpcServiceHandler.get()).thenReturn(this.rpcService);
    }

    @Test
    public void testInvokeRpc() throws Exception {
        final String identifier = "invoke-rpc-module:rpcTest";
        final NormalizedNode<?, ?> result = Mockito.mock(NormalizedNode.class);
        final NormalizedNodeContext payload = prepNNC(result);
        final UriInfo uriInfo = Mockito.mock(UriInfo.class);

        final NormalizedNodeContext rpc = this.invokeOperationsService.invokeRpc(identifier, payload, uriInfo);
        Assert.assertEquals(result, rpc.getData());
    }

    private NormalizedNodeContext prepNNC(final NormalizedNode result) {
        final InstanceIdentifierContext context = Mockito.mock(InstanceIdentifierContext.class);
        final RpcDefinition schemaNode = Mockito.mock(RpcDefinition.class);
        final QName qname = QName.create("invoke:rpc:module", "2013-12-3", "rpcTest");
        final SchemaPath schemaPath = SchemaPath.create(true, qname);
        Mockito.when(schemaNode.getPath()).thenReturn(schemaPath);
        Mockito.when(schemaNode.getQName()).thenReturn(qname);
        Mockito.when(context.getSchemaNode()).thenReturn(schemaNode);
        final NormalizedNode<?, ?> data = Mockito.mock(NormalizedNode.class);
        final DOMRpcResult domRpcResult = Mockito.mock(DOMRpcResult.class);
        final CheckedFuture<DOMRpcResult, DOMRpcException> checkdFuture = Futures.immediateCheckedFuture(domRpcResult);
        Mockito.when(this.rpcService.invokeRpc(schemaPath, data)).thenReturn(checkdFuture);
        Mockito.when(domRpcResult.getResult()).thenReturn(result);
        return new NormalizedNodeContext(context, data);
    }

}
