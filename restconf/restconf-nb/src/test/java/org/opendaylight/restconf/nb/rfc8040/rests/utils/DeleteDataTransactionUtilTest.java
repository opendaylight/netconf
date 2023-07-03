/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class DeleteDataTransactionUtilTest {
    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private DOMDataBroker mockDataBroker;
    @Mock
    private NetconfDataTreeService netconfService;

    @Before
    public void init() {
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).discardChanges();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).lock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
    }

    /**
     * Test of successful DELETE operation.
     */
    @Test
    public void deleteData() {
        // assert that data to delete exists
        when(readWrite.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of()))
            .thenReturn(immediateTrueFluentFuture());
        // test
        delete(new MdsalRestconfStrategy(mockDataBroker));
        delete(new NetconfRestconfStrategy(netconfService));
    }

    /**
     * Negative test for DELETE operation when data to delete does not exist. Error DATA_MISSING is expected.
     */
    @Test
    public void deleteDataNegativeTest() {
        // assert that data to delete does NOT exist
        when(readWrite.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of()))
            .thenReturn(immediateFalseFluentFuture());
        final NetconfDocumentedException exception = new NetconfDocumentedException("id",
            ErrorType.RPC, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR);
        final SettableFuture<? extends CommitInfo> ret = SettableFuture.create();
        ret.setException(new TransactionCommitFailedException(
            String.format("Commit of transaction %s failed", this), exception));

        doReturn(ret).when(netconfService).commit();

        // test and assert error
        deleteFail(new MdsalRestconfStrategy(mockDataBroker));
        deleteFail(new NetconfRestconfStrategy(netconfService));
    }

    private static void delete(final RestconfStrategy strategy) {
        final Response response = DeleteDataTransactionUtil.deleteData(strategy, YangInstanceIdentifier.of());
        // assert success
        assertEquals("Not expected response received", Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    private static void deleteFail(final RestconfStrategy strategy) {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> DeleteDataTransactionUtil.deleteData(strategy, YangInstanceIdentifier.of()));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }
}
