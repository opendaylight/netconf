/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMRpcException;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.handlers.RpcServiceHandler;
import org.opendaylight.yangtools.yang.common.RpcError;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfInvokeOperationsUtilTest {

    private static final TestData DATA = new TestData();

    private RpcServiceHandler serviceHandler;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMMountPoint moutPoint;

    @Before
    public void setUp() {
        serviceHandler = new RpcServiceHandler(rpcService);
    }

    @Test
    public void invokeRpcTest() {
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(DATA.output, Collections.emptyList());
        doReturn(immediateFluentFuture(mockResult)).when(rpcService).invokeRpc(DATA.rpc, DATA.input);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsUtil.invokeRpc(DATA.input, DATA.rpc, serviceHandler);
        Assert.assertTrue(rpcResult.getErrors().isEmpty());
        assertEquals(DATA.output, rpcResult.getResult());
    }

    @Test(expected = RestconfDocumentedException.class)
    public void invokeRpcErrorsAndCheckTestTest() {
        final DOMRpcException exception = new DOMRpcImplementationNotAvailableException(
                "No implementation of RPC " + DATA.errorRpc.toString() + " available.");
        doReturn(immediateFailedFluentFuture(exception)).when(rpcService).invokeRpc(DATA.errorRpc, DATA.input);
        final DOMRpcResult rpcResult =
                RestconfInvokeOperationsUtil.invokeRpc(DATA.input, DATA.errorRpc, serviceHandler);
        assertNull(rpcResult.getResult());
        final Collection<? extends RpcError> errorList = rpcResult.getErrors();
        assertEquals(1, errorList.size());
        final RpcError actual = errorList.iterator().next();
        assertEquals("No implementation of RPC " + DATA.errorRpc.toString() + " available.", actual.getMessage());
        assertEquals("operation-failed", actual.getTag());
        assertEquals(RpcError.ErrorType.RPC, actual.getErrorType());
        RestconfInvokeOperationsUtil.checkResponse(rpcResult);
    }

    @Test
    public void invokeRpcViaMountPointTest() {
        doReturn(Optional.ofNullable(rpcService)).when(moutPoint).getService(DOMRpcService.class);
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(DATA.output, Collections.emptyList());
        doReturn(immediateFluentFuture(mockResult)).when(rpcService).invokeRpc(DATA.rpc, DATA.input);
        final DOMRpcResult rpcResult =
                RestconfInvokeOperationsUtil.invokeRpcViaMountPoint(moutPoint, DATA.input, DATA.rpc);
        Assert.assertTrue(rpcResult.getErrors().isEmpty());
        assertEquals(DATA.output, rpcResult.getResult());
    }

    @Test(expected = RestconfDocumentedException.class)
    public void invokeRpcMissingMountPointServiceTest() {
        doReturn(Optional.empty()).when(moutPoint).getService(DOMRpcService.class);
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(DATA.output, Collections.emptyList());
        final DOMRpcResult rpcResult =
                RestconfInvokeOperationsUtil.invokeRpcViaMountPoint(moutPoint, DATA.input, DATA.rpc);
    }

    @Test
    public void checkResponseTest() {
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(DATA.output, Collections.emptyList());
        doReturn(immediateFluentFuture(mockResult)).when(rpcService).invokeRpc(DATA.rpc, DATA.input);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsUtil.invokeRpc(DATA.input, DATA.rpc, serviceHandler);
        Assert.assertTrue(rpcResult.getErrors().isEmpty());
        assertEquals(DATA.output, rpcResult.getResult());
        assertNotNull(RestconfInvokeOperationsUtil.checkResponse(rpcResult));
    }
}
