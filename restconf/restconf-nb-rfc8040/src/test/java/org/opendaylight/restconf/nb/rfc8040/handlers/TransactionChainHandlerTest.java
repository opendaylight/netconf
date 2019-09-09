/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import com.google.common.collect.ClassToInstanceMap;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;

public class TransactionChainHandlerTest {

    private TransactionChainHandler transactionChainHandler;
    private static final String EXCEPTION_MESSAGE = "(TEST) Unsupported Method";

    @After
    public void shutdown() {
        this.transactionChainHandler.close();
    }

    @Test
    public void transactionChainTest() {
        this.transactionChainHandler = new TransactionChainHandler(new DataBrokerLocal());
        final DOMTransactionChain chain1 = this.transactionChainHandler.get();
        final DOMTransactionChain chain2 = this.transactionChainHandler.get();
        final DOMTransactionChain chain3 = this.transactionChainHandler.get();
        Assert.assertNotNull(chain1);
        Assert.assertNotNull(chain2);
        Assert.assertNotEquals(chain1, chain2);
        chain1.close();
        Assert.assertFalse(this.transactionChainHandler.removeTransactionChain(chain1));

        try {
            chain2.newReadOnlyTransaction();
        } catch (RestconfDocumentedException e) {
            Assert.assertEquals(e.getCause().getLocalizedMessage(), EXCEPTION_MESSAGE);
        }
        Assert.assertFalse(this.transactionChainHandler.removeTransactionChain(chain2));

        Assert.assertTrue(this.transactionChainHandler.removeTransactionChain(chain3));
        Assert.assertFalse(this.transactionChainHandler.removeTransactionChain(chain3));
    }

    private final class TxChainLocal implements DOMTransactionChain {
        DOMTransactionChainListener listener;

        TxChainLocal(DOMTransactionChainListener listener) {
            this.listener = listener;
        }

        @Override
        public DOMDataTreeReadTransaction newReadOnlyTransaction() {
            final DOMDataTreeTransaction domDataTreeTransaction = Mockito.mock(DOMDataTreeTransaction.class);
            listener.onTransactionChainFailed(this,
                    domDataTreeTransaction, new Throwable(EXCEPTION_MESSAGE));
            return null;
        }

        @Override
        public DOMDataTreeWriteTransaction newWriteOnlyTransaction() {
            return null;
        }

        @Override
        public DOMDataTreeReadWriteTransaction newReadWriteTransaction() {
            return null;
        }

        @Override
        public void close() {
            listener.onTransactionChainSuccessful(this);
        }
    }

    private final class DataBrokerLocal implements DOMDataBroker {

        @Override
        public @NonNull DOMTransactionChain createTransactionChain(DOMTransactionChainListener listener) {
            return new TxChainLocal(listener);
        }

        @Override
        public @NonNull DOMTransactionChain createMergingTransactionChain(DOMTransactionChainListener listener) {
            return null;
        }

        @Override
        public @NonNull ClassToInstanceMap<DOMDataBrokerExtension> getExtensions() {
            return null;
        }

        @Override
        public DOMDataTreeReadTransaction newReadOnlyTransaction() {
            return null;
        }

        @Override
        public DOMDataTreeWriteTransaction newWriteOnlyTransaction() {
            return null;
        }

        @Override
        public DOMDataTreeReadWriteTransaction newReadWriteTransaction() {
            return null;
        }
    }
}
