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
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.api.http.NotFoundException;
import org.opendaylight.restconfsb.communicator.impl.xml.draft04.RestconfErrorXmlParser;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class ReadOnlyTxTest {

    @Mock
    private RestconfFacade facade;
    private TestData data;
    private ReadOnlyTx tx;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        data = new TestData();
        tx = new ReadOnlyTx(facade);
        doReturn(Futures.immediateFuture(Optional.of(data.data))).when(facade).getData(data.datastore, data.path);
        doReturn(Futures.immediateFuture(null)).when(facade).headData(data.datastore, data.path);
        doReturn(Futures.immediateFuture(Optional.absent())).when(facade).getData(data.datastore, data.nonExistPath);
        doReturn(Futures.immediateFailedFuture(new NotFoundException("resource not found"))).when(facade).headData(data.datastore, data.nonExistPath);
        final HttpException httpException = new HttpException(409, data.errorMessage);
        doReturn(Futures.immediateFailedFuture(httpException)).when(facade).getData(data.datastore, data.errorPath);
        doReturn(Futures.immediateFailedFuture(httpException)).when(facade).headData(data.datastore, data.errorPath);
        doReturn(new RestconfErrorXmlParser().parseErrors(httpException.getMsg())).when(facade).parseErrors(httpException);
    }

    @Test
    public void testRead() throws Exception {
        final Optional<NormalizedNode<?, ?>> normalizedNodeOptional = tx.read(data.datastore, data.path).checkedGet();
        Assert.assertEquals(data.data, normalizedNodeOptional.get());
    }

    @Test
    public void testReadEmpty() throws Exception {
        final Optional<NormalizedNode<?, ?>> normalizedNodeOptional = tx.read(data.datastore, data.nonExistPath).checkedGet();
        Assert.assertFalse(normalizedNodeOptional.isPresent());
    }

    @Test
    public void testReadError() throws Exception {
        try {
            tx.read(data.datastore, data.errorPath).checkedGet();
        } catch (ReadFailedException e) {
            final List<RpcError> errorList = e.getErrorList();
            Assert.assertEquals(1, errorList.size());
            final RpcError actual = errorList.get(0);
            Assert.assertEquals("Lock failed, lock already held", actual.getMessage());
            Assert.assertEquals("lock-denied", actual.getTag());
            Assert.assertEquals(RpcError.ErrorType.PROTOCOL, actual.getErrorType());
        }
    }

    @Test
    public void testExists() throws Exception {
        final Boolean exists = tx.exists(data.datastore, data.path).checkedGet();
        Assert.assertTrue(exists);
    }

    @Test
    public void testExistsNonExistent() throws Exception {
        final Boolean exists = tx.exists(data.datastore, data.nonExistPath).checkedGet();
        Assert.assertFalse(exists);
    }

    @Test
    public void testExistsError() throws Exception {
        try {
            tx.exists(data.datastore, data.errorPath).checkedGet();
        } catch (ReadFailedException e) {
            final List<RpcError> errorList = e.getErrorList();
            Assert.assertEquals(1, errorList.size());
            final RpcError actual = errorList.get(0);
            Assert.assertEquals("Lock failed, lock already held", actual.getMessage());
            Assert.assertEquals("lock-denied", actual.getTag());
            Assert.assertEquals(RpcError.ErrorType.PROTOCOL, actual.getErrorType());
        }
    }
}