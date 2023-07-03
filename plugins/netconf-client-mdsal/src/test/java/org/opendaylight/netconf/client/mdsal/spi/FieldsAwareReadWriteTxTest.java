/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class FieldsAwareReadWriteTxTest {
    @Mock
    private NetconfDOMFieldsReadTransaction delegateReadTx;
    @Mock
    private DOMDataTreeWriteTransaction delegateWriteTx;

    @Test
    public void testReadWithFields() {
        final FieldsAwareReadWriteTx tx = new FieldsAwareReadWriteTx(delegateReadTx, delegateWriteTx);
        tx.read(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId(),
            List.of(YangInstanceIdentifier.of()));
        verify(delegateReadTx).read(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId(),
            List.of(YangInstanceIdentifier.of()));
    }
}