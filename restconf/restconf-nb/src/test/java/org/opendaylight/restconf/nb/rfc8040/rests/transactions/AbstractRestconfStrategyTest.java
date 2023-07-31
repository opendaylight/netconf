/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import javax.ws.rs.core.Response.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.DeleteDataTransactionUtil;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

abstract class AbstractRestconfStrategyTest {
    /**
     * Test of successful DELETE operation.
     */
    @Test
    public final void testDeleteData() {
        final var response = DeleteDataTransactionUtil.deleteData(testDeleteDataStrategy(),
            YangInstanceIdentifier.of());
        // assert success
        assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    abstract @NonNull RestconfStrategy testDeleteDataStrategy();

    /**
     * Negative test for DELETE operation when data to delete does not exist. Error DATA_MISSING is expected.
     */
    @Test
    public final void testNegativeDeleteData() {
        final var strategy = testNegativeDeleteDataStrategy();
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> DeleteDataTransactionUtil.deleteData(strategy, YangInstanceIdentifier.of()));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    abstract @NonNull RestconfStrategy testNegativeDeleteDataStrategy();
}
