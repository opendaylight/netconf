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

import com.google.common.util.concurrent.Futures;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.TransactionVarsWrapper;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class DeleteDataTransactionUtilTest {
    @Mock
    private DOMTransactionChain transactionChain;
    @Mock
    private InstanceIdentifierContext<?> context;
    @Mock
    private DOMDataReadWriteTransaction readWrite;
    @Mock
    private DOMDataBroker mockDataBroker;

    private TransactionChainHandler transactionChainHandler;

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
        Mockito.when(this.transactionChain.newReadWriteTransaction()).thenReturn(this.readWrite);
        Mockito.when(this.readWrite.submit()).thenReturn(Futures.immediateCheckedFuture(null));
        Mockito.when(this.context.getInstanceIdentifier()).thenReturn(YangInstanceIdentifier.EMPTY);

        Mockito.doReturn(transactionChain).when(mockDataBroker).createTransactionChain(Mockito.any());
        transactionChainHandler = new TransactionChainHandler(mockDataBroker);
    }

    /**
     * Test of successful DELETE operation.
     */
    @Test
    public void deleteData() throws Exception {
        // assert that data to delete exists
        Mockito.when(this.transactionChain.newReadWriteTransaction().exists(LogicalDatastoreType.CONFIGURATION,
                YangInstanceIdentifier.EMPTY))
                .thenReturn(Futures.immediateCheckedFuture(Boolean.TRUE));

        // test
        final Response response = DeleteDataTransactionUtil.deleteData(
                new TransactionVarsWrapper(this.context, null, transactionChainHandler));

        // assert success
        assertEquals("Not expected response received", Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    /**
     * Negative test for DELETE operation when data to delete does not exist. Error 404 is expected.
     */
    @Test
    public void deleteDataNegativeTest() throws Exception {
        // assert that data to delete does NOT exist
        Mockito.when(this.transactionChain.newReadWriteTransaction().exists(LogicalDatastoreType.CONFIGURATION,
                YangInstanceIdentifier.EMPTY))
                .thenReturn(Futures.immediateCheckedFuture(Boolean.FALSE));

        // test and assert error
        try {
            DeleteDataTransactionUtil.deleteData(new TransactionVarsWrapper(this.context, null,
                    transactionChainHandler));
            fail("Delete operation should fail due to missing data");
        } catch (final RestconfDocumentedException e) {
            assertEquals(ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals(404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }
}
