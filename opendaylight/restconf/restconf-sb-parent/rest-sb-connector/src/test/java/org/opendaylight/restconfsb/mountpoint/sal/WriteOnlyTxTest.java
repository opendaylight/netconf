/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.api.http.NotFoundException;
import org.opendaylight.restconfsb.communicator.impl.xml.draft04.RestconfErrorXmlParser;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class WriteOnlyTxTest {

    @Mock
    private RestconfFacade facade;
    private TestData data;

    private WriteOnlyTx tx;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        data = new TestData();

        doReturn(Futures.immediateFuture(null)).when(facade).putConfig(data.path2, data.data2);
        doReturn(Futures.immediateFuture(null)).when(facade).putConfig(data.path, data.data);
        doReturn(Futures.immediateFuture(null)).when(facade).putConfig((YangInstanceIdentifier) any(), eq(data.unkeyedListEntryNode));
        doReturn(Futures.immediateFuture(null)).when(facade).putConfig(data.nonExistPath, data.data);
        doReturn(Futures.immediateFuture(null)).when(facade).patchConfig(data.path, data.data);
        doReturn(Futures.immediateFuture(null)).when(facade).patchConfig(data.path2, data.data2);
        doReturn(Futures.immediateFuture(null)).when(facade).patchConfig((YangInstanceIdentifier) any(), eq(data.unkeyedListEntryNode));
        doReturn(Futures.immediateFuture(null)).when(facade).headData(data.datastore, data.path);
        doReturn(Futures.immediateFuture(null)).when(facade).headData(data.datastore, data.path2);
        doReturn(Futures.immediateFuture(null)).when(facade).headData(eq(data.datastore), (YangInstanceIdentifier) any());
        doReturn(Futures.immediateFuture(null)).when(facade).deleteConfig(data.path);
        doReturn(Futures.immediateFailedFuture(new NotFoundException(""))).when(facade).headData(data.datastore, data.nonExistPath);
        doReturn(Futures.immediateFailedFuture(new NotFoundException(""))).when(facade).headData(data.datastore, data.errorPath);
        final HttpException httpException = new HttpException(409, data.errorMessage);
        doReturn(Futures.immediateFailedFuture(httpException)).when(facade).putConfig(data.errorPath, data.data);
        doReturn(new RestconfErrorXmlParser().parseErrors(httpException.getMsg())).when(facade).parseErrors(httpException);
        tx = new WriteOnlyTx(facade);
    }

    @Test
    public void testPut() throws Exception {
        tx.put(data.datastore, data.path, data.data);
        tx.submit().checkedGet();
        verify(facade).putConfig(data.path, data.data);
    }

    @Test
    public void testPutListWithKey() throws Exception {
        tx.put(data.datastore, data.path3, data.listData);
        tx.submit().checkedGet();
        verify(facade).putConfig(data.path, data.data);
        verify(facade).putConfig(data.path2, data.data2);
    }

    @Test
    public void testPutListWithoutKey() throws Exception {
        tx.put(data.datastore, data.path3, data.listUnkeyedData);
        tx.submit().checkedGet();
        verify(facade).putConfig((YangInstanceIdentifier) any(), eq(data.unkeyedListEntryNode));
    }

    @Test
    public void testMerge() throws Exception {
        tx.merge(data.datastore, data.path, data.data);
        tx.submit().checkedGet();
        verify(facade).patchConfig(data.path, data.data);
    }

    @Test
    public void testMergeListWithKey() throws Exception {
        tx.merge(data.datastore, data.path3, data.listData);
        tx.submit().checkedGet();
        verify(facade).patchConfig(data.path, data.data);
        verify(facade).patchConfig(data.path2, data.data2);
    }

    @Test
    public void testMergeListwithoutKey() throws Exception {
        tx.merge(data.datastore, data.path3, data.listUnkeyedData);
        tx.submit().checkedGet();
        verify(facade).patchConfig((YangInstanceIdentifier) any(), eq(data.unkeyedListEntryNode));
    }

    @Test
    public void testMergeNonExist() throws Exception {
        tx.merge(data.datastore, data.nonExistPath, data.data);
        tx.submit().checkedGet();
        verify(facade).putConfig(data.nonExistPath, data.data);
    }

    @Test
    public void testDelete() throws Exception {
        tx.delete(data.datastore, data.path);
        tx.submit().checkedGet();
        verify(facade).deleteConfig(data.path);
    }

    @Test
    public void testSubmit() throws Exception {
        tx.put(data.datastore, data.path, data.data);
        tx.merge(data.datastore, data.path, data.data);
        tx.merge(data.datastore, data.nonExistPath, data.data);
        tx.submit().checkedGet();
        final InOrder order = inOrder(facade);
        order.verify(facade).putConfig(data.path, data.data);
        order.verify(facade).patchConfig(data.path, data.data);
        order.verify(facade).putConfig(data.nonExistPath, data.data);
    }

    @Test
    public void testSubmitError() throws Exception {
        tx.put(data.datastore, data.path, data.data);
        tx.merge(data.datastore, data.path, data.data);
        tx.merge(data.datastore, data.errorPath, data.data);
        tx.merge(data.datastore, data.nonExistPath, data.data);
        try {
            tx.submit().checkedGet();
            final InOrder order = inOrder(facade);
            order.verify(facade).putConfig(data.path, data.data);
            order.verify(facade).patchConfig(data.path, data.data);
            order.verify(facade).putConfig(data.errorPath, data.data);
            order.verify(facade).putConfig(data.nonExistPath, data.data);
        } catch (TransactionCommitFailedException e) {
            final List<RpcError> errorList = e.getErrorList();
            Assert.assertEquals(1, errorList.size());
            final RpcError actual = errorList.get(0);
            Assert.assertEquals("Lock failed, lock already held", actual.getMessage());
            Assert.assertEquals("lock-denied", actual.getTag());
            Assert.assertEquals(RpcError.ErrorType.PROTOCOL, actual.getErrorType());
        }
    }

    @Test
    public void testModifyAfterSubmit() throws Exception {
        tx.put(data.datastore, data.path, data.data);
        tx.submit().checkedGet();
        try {
            tx.put(data.datastore, data.path, data.data);
            Assert.fail("Transaction was submitted. Subsequent calls to put should not be possible");
        } catch (IllegalStateException e) {
            //expected exception
        }
        try {
            tx.merge(data.datastore, data.path, data.data);
            Assert.fail("Transaction was submitted. Subsequent calls to merge should not be possible");
        } catch (IllegalStateException e) {
            //expected exception
        }
        try {
            tx.delete(data.datastore, data.path);
            Assert.fail("Transaction was submitted. Subsequent calls to delete should not be possible");
        } catch (IllegalStateException e) {
            //expected exception
        }
        try {
            tx.submit().checkedGet();
            Assert.fail("Transaction was submitted. Subsequent calls to submit should not be possible");
        } catch (IllegalStateException e) {
            //expected exception
        }
    }
}