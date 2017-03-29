/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal;

import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class RestconfDataBroker implements DOMDataBroker {

    private static final String NOT_IMPLEMENTED = "Not implemented";
    private final RestconfFacade facade;

    public RestconfDataBroker(final RestconfFacade facade) {
        this.facade = facade;
    }

    @Override
    public DOMDataReadOnlyTransaction newReadOnlyTransaction() {
        return new ReadOnlyTx(facade);
    }

    @Override
    public DOMDataReadWriteTransaction newReadWriteTransaction() {
        return new ReadWriteTx(new ReadOnlyTx(facade), new WriteOnlyTx(facade));
    }

    @Override
    public DOMDataWriteTransaction newWriteOnlyTransaction() {
        return new WriteOnlyTx(facade);
    }

    @Override
    public ListenerRegistration<DOMDataChangeListener> registerDataChangeListener(final LogicalDatastoreType logicalDatastoreType,
                                                                                  final YangInstanceIdentifier yangInstanceIdentifier,
                                                                                  final DOMDataChangeListener domDataChangeListener,
                                                                                  final DataChangeScope dataChangeScope) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public DOMTransactionChain createTransactionChain(final TransactionChainListener transactionChainListener) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }


    @Nonnull
    @Override
    public Map<Class<? extends DOMDataBrokerExtension>, DOMDataBrokerExtension> getSupportedExtensions() {
        return Collections.emptyMap();
    }
}
