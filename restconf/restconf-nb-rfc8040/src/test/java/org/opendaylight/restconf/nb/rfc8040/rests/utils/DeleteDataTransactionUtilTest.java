/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import com.google.common.util.concurrent.SettableFuture;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class DeleteDataTransactionUtilTest {
    @Mock
    private DOMTransactionChain transactionChain;
    @Mock
    private InstanceIdentifierContext<?> context;
    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private DOMDataBroker mockDataBroker;
    @Mock
    private NetconfDataTreeService netconfService;

    private TransactionChainHandler transactionChainHandler;

    @Before
    public void init() {
        Mockito.when(this.transactionChain.newReadWriteTransaction()).thenReturn(this.readWrite);
        Mockito.doReturn(CommitInfo.emptyFluentFuture()).when(this.readWrite).commit();
        Mockito.doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());
        Mockito.when(this.context.getInstanceIdentifier()).thenReturn(YangInstanceIdentifier.empty());

        Mockito.doReturn(transactionChain).when(mockDataBroker).createTransactionChain(Mockito.any());
        transactionChainHandler = new TransactionChainHandler(mockDataBroker);
    }

    /**
     * Test of successful DELETE operation.
     */
    @Test
    public void deleteData() {
        // assert that data to delete exists
        Mockito.when(this.transactionChain.newReadWriteTransaction().exists(LogicalDatastoreType.CONFIGURATION,
                YangInstanceIdentifier.empty())).thenReturn(immediateTrueFluentFuture());
        // test
        delete(new MdsalRestconfStrategy(transactionChainHandler));
        delete(new NetconfRestconfStrategy(netconfService));
    }

    /**
     * Negative test for DELETE operation when data to delete does not exist. Error DATA_MISSING is expected.
     */
    @Test
    public void deleteDataNegativeTest() {
        // assert that data to delete does NOT exist
        Mockito.when(this.transactionChain.newReadWriteTransaction().exists(LogicalDatastoreType.CONFIGURATION,
                YangInstanceIdentifier.empty())).thenReturn(immediateFalseFluentFuture());
        final NetconfDocumentedException exception = new NetconfDocumentedException("id",
            DocumentedException.ErrorType.RPC, DocumentedException.ErrorTag.from("data-missing"),
            DocumentedException.ErrorSeverity.ERROR);
        final SettableFuture<? extends CommitInfo> ret = SettableFuture.create();
        ret.setException(new TransactionCommitFailedException(
            String.format("Commit of transaction %s failed", this), exception));

        Mockito.when(this.netconfService.commit(any())).thenAnswer(invocation -> ret);

        // test and assert error
        deleteFail(new MdsalRestconfStrategy(transactionChainHandler));
        deleteFail(new NetconfRestconfStrategy(netconfService));
    }

    private void delete(final RestconfStrategy strategy) {
        final Response response = DeleteDataTransactionUtil.deleteData(strategy, context.getInstanceIdentifier());
        // assert success
        assertEquals("Not expected response received", Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    private void deleteFail(final RestconfStrategy strategy) {
        try {
            DeleteDataTransactionUtil.deleteData(strategy, context.getInstanceIdentifier());
            fail("Delete operation should fail due to missing data");
        } catch (final RestconfDocumentedException e) {
            assertEquals(ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
        }
    }
}
