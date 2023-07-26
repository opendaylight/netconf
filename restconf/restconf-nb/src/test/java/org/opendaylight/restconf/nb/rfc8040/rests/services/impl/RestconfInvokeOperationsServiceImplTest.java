/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcException;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfInvokeOperationsServiceImplTest {
    private static final QName RPC = QName.create("ns", "2015-02-28", "test-rpc");
    private static final ContainerNode INPUT = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(RPC, "input")))
        .withChild(ImmutableNodes.leafNode(QName.create(RPC, "content"), "test"))
        .build();
    private static final ContainerNode OUTPUT = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(RPC, "output")))
        .withChild(ImmutableNodes.leafNode(QName.create(RPC, "content"), "operation result"))
        .build();

    private static EffectiveModelContext CONTEXT;

    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMMountPoint mountPoint;
    @Mock
    private DOMMountPointService mountPointService;
    private RestconfInvokeOperationsServiceImpl invokeOperationsService;

    @BeforeClass
    public static void beforeClass() {
        CONTEXT = YangParserTestUtils.parseYangResourceDirectory("/invoke-rpc");
    }

    @Before
    public void setup() {
        invokeOperationsService = new RestconfInvokeOperationsServiceImpl(rpcService, mountPointService,
            new StreamsConfiguration(0, 1, 0, false));
    }

    @Test
    public void testInvokeRpcWithNonEmptyOutput() {
        final ContainerNode result = mock(ContainerNode.class);
        doReturn(false).when(result).isEmpty();

        final AsyncResponse ar = mock(AsyncResponse.class);
        final ArgumentCaptor<NormalizedNodePayload> response = ArgumentCaptor.forClass(NormalizedNodePayload.class);
        invokeOperationsService.invokeRpc("invoke-rpc-module:rpcTest", prepNNC(result), mock(UriInfo.class), ar);
        verify(ar).resume(response.capture());

        assertSame(result, response.getValue().getData());
    }

    @Test
    public void testInvokeRpcWithEmptyOutput() {
        final ContainerNode result = mock(ContainerNode.class);
        doReturn(true).when(result).isEmpty();

        final AsyncResponse ar = mock(AsyncResponse.class);
        final ArgumentCaptor<Throwable> response = ArgumentCaptor.forClass(Throwable.class);
        invokeOperationsService.invokeRpc("invoke-rpc-module:rpcTest", prepNNC(result), mock(UriInfo.class), ar);
        verify(ar).resume(response.capture());

        final Throwable failure = response.getValue();
        assertThat(failure, instanceOf(WebApplicationException.class));
        assertEquals(Status.NO_CONTENT.getStatusCode(), ((WebApplicationException) failure).getResponse().getStatus());
    }

    @Test
    public void invokeRpcTest() throws InterruptedException, ExecutionException {
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(OUTPUT, List.of());
        doReturn(immediateFluentFuture(mockResult)).when(rpcService).invokeRpc(RPC, INPUT);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsServiceImpl.invokeRpc(INPUT, RPC, rpcService).get();
        assertTrue(rpcResult.errors().isEmpty());
        assertEquals(OUTPUT, rpcResult.value());
    }

    @Test
    public void invokeRpcErrorsAndCheckTestTest() throws InterruptedException, ExecutionException {
        final QName errorRpc = QName.create(RPC, "error-rpc");
        final DOMRpcException exception = new DOMRpcImplementationNotAvailableException(
                "No implementation of RPC " + errorRpc + " available.");
        doReturn(immediateFailedFluentFuture(exception)).when(rpcService).invokeRpc(errorRpc, INPUT);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsServiceImpl.invokeRpc(INPUT, errorRpc, rpcService).get();
        assertNull(rpcResult.value());
        final Collection<? extends RpcError> errorList = rpcResult.errors();
        assertEquals(1, errorList.size());
        final RpcError actual = errorList.iterator().next();
        assertEquals("No implementation of RPC " + errorRpc + " available.", actual.getMessage());
        assertEquals(ErrorTag.OPERATION_FAILED, actual.getTag());
        assertEquals(ErrorType.RPC, actual.getErrorType());
    }

    @Test
    public void invokeRpcViaMountPointTest() throws InterruptedException, ExecutionException {
        doReturn(Optional.ofNullable(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(OUTPUT, List.of());
        doReturn(immediateFluentFuture(mockResult)).when(rpcService).invokeRpc(RPC, INPUT);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsServiceImpl.invokeRpc(INPUT, RPC, mountPoint).get();
        assertTrue(rpcResult.errors().isEmpty());
        assertEquals(OUTPUT, rpcResult.value());
    }

    @Test
    public void invokeRpcMissingMountPointServiceTest() {
        doReturn(Optional.empty()).when(mountPoint).getService(DOMRpcService.class);
        assertThrows(RestconfDocumentedException.class,
            () -> RestconfInvokeOperationsServiceImpl.invokeRpc(INPUT, RPC, mountPoint));
    }

    @Test
    public void checkResponseTest() throws InterruptedException, ExecutionException {
        doReturn(immediateFluentFuture(new DefaultDOMRpcResult(OUTPUT, List.of())))
            .when(rpcService).invokeRpc(RPC, INPUT);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsServiceImpl.invokeRpc(INPUT, RPC, rpcService).get();
        assertTrue(rpcResult.errors().isEmpty());
        assertEquals(OUTPUT, rpcResult.value());
    }

    private NormalizedNodePayload prepNNC(final ContainerNode result) {
        final QName qname = QName.create("invoke:rpc:module", "2013-12-03", "rpc-test");
        final RpcDefinition schemaNode = CONTEXT.getOperations().stream().filter(rpc -> rpc.getQName().equals(qname))
            .findFirst()
            .orElseThrow();

        final ContainerNode data = mock(ContainerNode.class);
        final DOMRpcResult domRpcResult = mock(DOMRpcResult.class);
        doReturn(immediateFluentFuture(domRpcResult)).when(rpcService).invokeRpc(qname, data);
        doReturn(result).when(domRpcResult).value();
        return NormalizedNodePayload.of(
            InstanceIdentifierContext.ofRpcInput(CONTEXT, schemaNode, null), data);
    }
}
