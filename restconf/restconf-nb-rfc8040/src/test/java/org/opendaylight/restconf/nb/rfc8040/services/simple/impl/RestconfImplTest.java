/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.simple.impl;

import com.google.common.util.concurrent.CheckedFuture;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class RestconfImplTest {

    @Test
    public void restImplTest() throws Exception {
        final SchemaContext schemaContext =
                YangParserTestUtils.parseYangSources(TestRestconfUtils.loadFiles("/restconf/impl"));

        final TransactionChainHandler txHandler = Mockito.mock(TransactionChainHandler.class);
        final DOMTransactionChain domTx = Mockito.mock(DOMTransactionChain.class);
        Mockito.when(txHandler.get()).thenReturn(domTx);
        final DOMDataWriteTransaction wTx = Mockito.mock(DOMDataWriteTransaction.class);
        Mockito.when(domTx.newWriteOnlyTransaction()).thenReturn(wTx);
        final CheckedFuture<Void, TransactionCommitFailedException> checked = Mockito.mock(CheckedFuture.class);
        Mockito.when(wTx.submit()).thenReturn(checked);
        Mockito.when(checked.checkedGet()).thenReturn(null);
        final SchemaContextHandler schemaContextHandler = new SchemaContextHandler(txHandler);
        schemaContextHandler.onGlobalContextUpdated(schemaContext);

        final RestconfImpl restconfImpl = new RestconfImpl(schemaContextHandler);
        final NormalizedNodeContext libraryVersion = restconfImpl.getLibraryVersion();
        final LeafNode<?> value = (LeafNode<?>) libraryVersion.getData();
        Assert.assertEquals(IetfYangLibrary.REVISION, value.getValue());
    }
}
