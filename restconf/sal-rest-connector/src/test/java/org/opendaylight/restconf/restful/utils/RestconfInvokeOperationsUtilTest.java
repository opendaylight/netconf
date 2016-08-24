/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.util.Collections;
import javax.ws.rs.WebApplicationException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.restconf.handlers.RpcServiceHandler;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

public class RestconfInvokeOperationsUtilTest {

    private static final TestData data = new TestData();

    private RpcServiceHandler serviceHandler;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMMountPoint moutPoint;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        serviceHandler = new RpcServiceHandler(rpcService);
    }

    @Test
    public void invokeRpcTest() {
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(data.output, Collections.emptyList());
        doReturn(Futures.immediateCheckedFuture(mockResult)).when(rpcService).invokeRpc(data.rpc, data.input);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsUtil.invokeRpc(data.input, data.rpc, serviceHandler);
        Assert.assertTrue(rpcResult.getErrors().isEmpty());
        assertEquals(data.output, rpcResult.getResult());
    }

    @Test
    public void invokeRpcErrorsTest() {
        final RpcError error = RpcResultBuilder.newError(RpcError.ErrorType.PROTOCOL, null, data.errorMessage);
        final WebApplicationException mockResult = new RestconfDocumentedException(new RestconfError(error));
        doReturn(Futures.immediateFailedCheckedFuture(mockResult)).when(rpcService).invokeRpc(data.errorRpc, data.input);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsUtil.invokeRpc(data.input, data.errorRpc, serviceHandler);
        assertNull(rpcResult);
    }

    @Test
    public void invokeRpcViaMountPointTest() {
        doReturn(Optional.fromNullable(rpcService)).when(moutPoint).getService(DOMRpcService.class);
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(data.output, Collections.emptyList());
        doReturn(Futures.immediateCheckedFuture(mockResult)).when(rpcService).invokeRpc(data.rpc, data.input);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsUtil.invokeRpcViaMountPoint(moutPoint, data.input, data.rpc);
        Assert.assertTrue(rpcResult.getErrors().isEmpty());
        assertEquals(data.output, rpcResult.getResult());
    }

    @Test(expected = RestconfDocumentedException.class)
    public void invokeRpcMissingMountPointServiceTest() {
        doReturn(Optional.absent()).when(moutPoint).getService(DOMRpcService.class);
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(data.output, Collections.emptyList());
        doReturn(Futures.immediateCheckedFuture(mockResult)).when(rpcService).invokeRpc(data.rpc, data.input);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsUtil.invokeRpcViaMountPoint(moutPoint, data.input, data.rpc);
    }

    @Test
    public void checkResponseTest() {
        final DOMRpcResult mockResult = new DefaultDOMRpcResult(data.output, Collections.emptyList());
        doReturn(Futures.immediateCheckedFuture(mockResult)).when(rpcService).invokeRpc(data.rpc, data.input);
        final DOMRpcResult rpcResult = RestconfInvokeOperationsUtil.invokeRpc(data.input, data.rpc, serviceHandler);
        Assert.assertTrue(rpcResult.getErrors().isEmpty());
        assertEquals(data.output, rpcResult.getResult());
        assertNotNull(RestconfInvokeOperationsUtil.checkResponse(rpcResult));
    }

}
