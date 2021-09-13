/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMRpcException;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfInvokeOperationsUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfInvokeOperationsServiceImplTest {
    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/invoke-rpc";

    private static final QName RPC = QName.create("ns", "2015-02-28", "test-rpc");
    private static final ContainerNode INPUT = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(RPC, "input")))
        .withChild(ImmutableNodes.leafNode(QName.create(RPC, "content"), "test"))
        .build();
    private static final ContainerNode OUTPUT = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(RPC, "output")))
        .withChild(ImmutableNodes.leafNode(QName.create(RPC, "content"), "operation result"))
        .build();

    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMMountPoint mountPoint;
    private RestconfInvokeOperationsServiceImpl invokeOperationsService;

    @Before
    public void setup() throws Exception {
        final EffectiveModelContext contextRef =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT));
        final DOMDataBroker dataBroker = mock(DOMDataBroker.class);
        final DOMDataTreeWriteTransaction wTx = mock(DOMDataTreeWriteTransaction.class);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(wTx);
        doReturn(CommitInfo.emptyFluentFuture()).when(wTx).commit();
        final SchemaContextHandler schemaContextHandler = new SchemaContextHandler(dataBroker,
            mock(DOMSchemaService.class));
        schemaContextHandler.onModelContextUpdated(contextRef);
        this.invokeOperationsService =
                new RestconfInvokeOperationsServiceImpl(this.rpcService, schemaContextHandler);
    }

    @Test
    public void testInvokeRpcWithNonEmptyOutput() {
        final String identifier = "invoke-rpc-module:rpcTest";
        final ContainerNode result = mock(ContainerNode.class);
        final LeafNode<?> outputChild = mock(LeafNode.class);
        doCallRealMethod().when(result).isEmpty();
        doReturn(List.of(outputChild)).when(result).body();

        final NormalizedNodeContext payload = prepNNC(result);
        final UriInfo uriInfo = mock(UriInfo.class);

        final NormalizedNodeContext rpc = this.invokeOperationsService.invokeRpc(identifier, payload, uriInfo);
        assertEquals(result, rpc.getData());
    }

    @Test
    public void testInvokeRpcWithEmptyOutput() {
        final String identifier = "invoke-rpc-module:rpcTest";
        final ContainerNode result = mock(ContainerNode.class);
        doReturn(true).when(result).isEmpty();

        final NormalizedNodeContext payload = prepNNC(result);
        final UriInfo uriInfo = mock(UriInfo.class);

        WebApplicationException exceptionToBeThrown = null;
        try {
            this.invokeOperationsService.invokeRpc(identifier, payload, uriInfo);
        } catch (final WebApplicationException exception) {
            exceptionToBeThrown = exception;

        }
        assertNotNull("WebApplicationException with status code 204 is expected.", exceptionToBeThrown);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), exceptionToBeThrown.getResponse().getStatus());
    }

    @Test
    public void invokeRpcTest() {
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(OUTPUT, List.of());
        doReturn(immediateFluentFuture(mockResult)).when(rpcService).invokeRpc(RPC, INPUT);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsUtil.invokeRpc(INPUT, RPC, rpcService);
        assertTrue(rpcResult.getErrors().isEmpty());
        assertEquals(OUTPUT, rpcResult.getResult());
    }

    @Test
    public void invokeRpcErrorsAndCheckTestTest() {
        final QName errorRpc = QName.create(RPC, "error-rpc");
        final DOMRpcException exception = new DOMRpcImplementationNotAvailableException(
                "No implementation of RPC " + errorRpc + " available.");
        doReturn(immediateFailedFluentFuture(exception)).when(rpcService).invokeRpc(errorRpc, INPUT);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsUtil.invokeRpc(INPUT, errorRpc, rpcService);
        assertNull(rpcResult.getResult());
        final Collection<? extends RpcError> errorList = rpcResult.getErrors();
        assertEquals(1, errorList.size());
        final RpcError actual = errorList.iterator().next();
        assertEquals("No implementation of RPC " + errorRpc + " available.", actual.getMessage());
        assertEquals("operation-failed", actual.getTag());
        assertEquals(RpcError.ErrorType.RPC, actual.getErrorType());

        assertThrows(RestconfDocumentedException.class, () -> RestconfInvokeOperationsUtil.checkResponse(rpcResult));
    }

    @Test
    public void invokeRpcViaMountPointTest() {
        doReturn(Optional.ofNullable(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(OUTPUT, List.of());
        doReturn(immediateFluentFuture(mockResult)).when(rpcService).invokeRpc(RPC, INPUT);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsUtil.invokeRpc(INPUT, RPC, mountPoint);
        assertTrue(rpcResult.getErrors().isEmpty());
        assertEquals(OUTPUT, rpcResult.getResult());
    }

    @Test
    public void invokeRpcMissingMountPointServiceTest() {
        doReturn(Optional.empty()).when(mountPoint).getService(DOMRpcService.class);
        assertThrows(RestconfDocumentedException.class,
            () -> RestconfInvokeOperationsUtil.invokeRpc(INPUT, RPC, mountPoint));
    }

    @Test
    public void checkResponseTest() {
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(OUTPUT, List.of());
        doReturn(immediateFluentFuture(mockResult)).when(rpcService).invokeRpc(RPC, INPUT);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsUtil.invokeRpc(INPUT, RPC, rpcService);
        assertTrue(rpcResult.getErrors().isEmpty());
        assertEquals(OUTPUT, rpcResult.getResult());
        assertNotNull(RestconfInvokeOperationsUtil.checkResponse(rpcResult));
    }

    private NormalizedNodeContext prepNNC(final NormalizedNode result) {
        final InstanceIdentifierContext<?> context = mock(InstanceIdentifierContext.class);
        final RpcDefinition schemaNode = mock(RpcDefinition.class);
        final QName qname = QName.create("invoke:rpc:module", "2013-12-03", "rpcTest");
        when(schemaNode.getQName()).thenReturn(qname);
        doReturn(schemaNode).when(context).getSchemaNode();
        final NormalizedNode data = mock(NormalizedNode.class);
        final DOMRpcResult domRpcResult = mock(DOMRpcResult.class);
        doReturn(immediateFluentFuture(domRpcResult)).when(rpcService).invokeRpc(qname, data);
        doReturn(result).when(domRpcResult).getResult();
        return new NormalizedNodeContext(context, data);
    }
}
