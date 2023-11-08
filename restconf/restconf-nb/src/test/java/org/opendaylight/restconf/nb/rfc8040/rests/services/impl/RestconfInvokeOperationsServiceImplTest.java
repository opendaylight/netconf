/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
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

    private static final DatabindContext CONTEXT =
        DatabindContext.ofModel(YangParserTestUtils.parseYangResourceDirectory("/invoke-rpc"));

    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMMountPoint mountPoint;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMNotificationService notificationService;

    private RestconfInvokeOperationsServiceImpl invokeOperationsService;
    private MdsalRestconfServer server;

    @Before
    public void setup() {
        server = new MdsalRestconfServer(dataBroker, rpcService, mountPointService);
        invokeOperationsService = new RestconfInvokeOperationsServiceImpl(() -> CONTEXT, server);
    }

    @Test
    public void testInvokeRpcWithNonEmptyOutput() {
        final var result = mock(ContainerNode.class);
        doReturn(false).when(result).isEmpty();

        prepNNC(result);
        final var ar = mock(AsyncResponse.class);
        final var captor = ArgumentCaptor.forClass(Response.class);
        invokeOperationsService.invokeRpcXML("invoke-rpc-module:rpc-test", new ByteArrayInputStream("""
            <input xmlns="invoke:rpc:module"/>
            """.getBytes(StandardCharsets.UTF_8)), mock(UriInfo.class), ar);
        verify(ar).resume(captor.capture());

        final var response = captor.getValue();
        assertEquals(200, response.getStatus());
        final var entity = (NormalizedNodePayload) response.getEntity();
        assertSame(result, entity.data());
    }

    @Test
    public void testInvokeRpcWithEmptyOutput() {
        final var result = mock(ContainerNode.class);
        doReturn(true).when(result).isEmpty();

        prepNNC(result);
        final var ar = mock(AsyncResponse.class);
        final var response = ArgumentCaptor.forClass(Response.class);
        invokeOperationsService.invokeRpcJSON("invoke-rpc-module:rpc-test", new ByteArrayInputStream("""
            {
              "invoke-rpc-module:input" : {
              }
            }
            """.getBytes(StandardCharsets.UTF_8)), mock(UriInfo.class), ar);
        verify(ar).resume(response.capture());

        assertEquals(204, response.getValue().getStatus());
    }

    @Test
    public void invokeRpcTest() throws Exception {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(OUTPUT, List.of()))).when(rpcService)
            .invokeRpc(RPC, INPUT);
        assertEquals(Optional.of(OUTPUT), Futures.getDone(server.getRestconfStrategy(CONTEXT.modelContext(), null)
            .invokeRpc(RPC, INPUT)));
    }

    @Test
    public void invokeRpcErrorsAndCheckTestTest() throws Exception {
        final var errorRpc = QName.create(RPC, "error-rpc");
        final var exception = new DOMRpcImplementationNotAvailableException(
                "No implementation of RPC " + errorRpc + " available.");
        doReturn(Futures.immediateFailedFuture(exception)).when(rpcService).invokeRpc(errorRpc, INPUT);
        final var ex = assertInstanceOf(RestconfDocumentedException.class,
            assertThrows(ExecutionException.class, () -> Futures.getDone(
                server.getRestconfStrategy(CONTEXT.modelContext(), null).invokeRpc(errorRpc, INPUT))).getCause());
        final var errorList = ex.getErrors();
        assertEquals(1, errorList.size());
        final var actual = errorList.iterator().next();
        assertEquals("No implementation of RPC " + errorRpc + " available.", actual.getErrorMessage());
        assertEquals(ErrorType.RPC, actual.getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, actual.getErrorTag());
    }

    @Test
    public void invokeRpcViaMountPointTest() throws Exception {
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(OUTPUT, List.of()))).when(rpcService)
            .invokeRpc(RPC, INPUT);
        assertEquals(Optional.of(OUTPUT), Futures.getDone(
            server.getRestconfStrategy(CONTEXT.modelContext(), mountPoint).invokeRpc(RPC, INPUT)));
    }

    @Test
    public void invokeRpcMissingMountPointServiceTest() {
        doReturn(Optional.empty()).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        final var strategy = server.getRestconfStrategy(CONTEXT.modelContext(), mountPoint);
        final var ex = assertInstanceOf(RestconfDocumentedException.class,
            assertThrows(ExecutionException.class, () -> Futures.getDone(strategy.invokeRpc(RPC, INPUT))).getCause());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_NOT_SUPPORTED, error.getErrorTag());
        assertEquals("RPC invocation is not available", error.getErrorMessage());
    }

    @Test
    public void checkResponseTest() throws Exception {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(OUTPUT, List.of())))
            .when(rpcService).invokeRpc(RPC, INPUT);
        assertEquals(Optional.of(OUTPUT), Futures.getDone(
            server.getRestconfStrategy(CONTEXT.modelContext(), null).invokeRpc(RPC, INPUT)));
    }

    private void prepNNC(final ContainerNode result) {
        final var qname = QName.create("invoke:rpc:module", "2013-12-03", "rpc-test");
        final var domRpcResult = mock(DOMRpcResult.class);
        doReturn(Futures.immediateFuture(domRpcResult)).when(rpcService).invokeRpc(eq(qname), any(ContainerNode.class));
        doReturn(result).when(domRpcResult).value();
    }
}
