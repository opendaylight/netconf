/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal;

import static org.mockito.Mockito.doReturn;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.util.Collection;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.impl.xml.draft04.RestconfErrorXmlParser;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class RestconfRpcServiceTest {

    @Mock
    private RestconfFacade facade;
    private TestData data;
    private RestconfRpcService service;
    private Optional<NormalizedNode<?, ?>> result;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        data = new TestData();
        service = new RestconfRpcService(facade);

        result = Optional.<NormalizedNode<?, ?>>of(data.output);
        doReturn(Futures.immediateFuture(result)).when(facade).postOperation(data.rpc, data.input);
        final HttpException httpException = new HttpException(409, data.errorMessage);
        doReturn(Futures.immediateFailedFuture(httpException)).when(facade).postOperation(data.errorRpc, data.input);
        doReturn(new RestconfErrorXmlParser().parseErrors(data.errorMessage)).when(facade).parseErrors(httpException);
    }

    @Test
    public void testInvokeRpc() throws Exception {
        final DOMRpcResult rpcResult = service.invokeRpc(data.rpc, data.input).checkedGet();
        Assert.assertTrue(rpcResult.getErrors().isEmpty());
        Assert.assertEquals(result.get(), rpcResult.getResult());
    }

    @Test
    public void testInvokeRpcError() throws Exception {
        final DOMRpcResult rpcResult = service.invokeRpc(data.errorRpc, data.input).checkedGet();
        Assert.assertNull(rpcResult.getResult());
        final Collection<RpcError> errorList = rpcResult.getErrors();
        Assert.assertEquals(1, errorList.size());
        final RpcError actual = errorList.iterator().next();
        Assert.assertEquals("Lock failed, lock already held", actual.getMessage());
        Assert.assertEquals("lock-denied", actual.getTag());
        Assert.assertEquals(RpcError.ErrorType.PROTOCOL, actual.getErrorType());
    }
}