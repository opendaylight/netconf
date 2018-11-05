/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;

/**
 * This class represent delegation wrapper for transaction variables.
 *
 */
public final class TransactionVarsWrapper {

    private final InstanceIdentifierContext<?> instanceIdentifier;
    private final DOMMountPoint mountPoint;
    private LogicalDatastoreType configuration = null;
    private final DOMTransactionChain transactionChain;
    private final TransactionChainHandler transactionChainHandler;

    /**
     * Set base type of variables, which ones we need for transaction.
     * {@link LogicalDatastoreType} is default set to null (to read all data
     * from DS - config + state).
     *
     * @param instanceIdentifier
     *             {@link InstanceIdentifierContext} of data for transaction
     * @param mountPoint
     *             mount point if is present
     * @param transactionChainHandler
     *             used to obtain the transaction chain for creating specific type of transaction
     *             in specific operation
     */
    public TransactionVarsWrapper(final InstanceIdentifierContext<?> instanceIdentifier, final DOMMountPoint mountPoint,
            final TransactionChainHandler transactionChainHandler) {
        this.instanceIdentifier = instanceIdentifier;
        this.mountPoint = mountPoint;
        this.transactionChainHandler = transactionChainHandler;
        transactionChain = transactionChainHandler.get();
    }

    /**
     * Get instance identifier of data.
     *
     * @return {@link InstanceIdentifierContext}
     */
    public InstanceIdentifierContext<?> getInstanceIdentifier() {
        return this.instanceIdentifier;
    }

    /**
     * Get mount point.
     *
     * @return {@link DOMMountPoint}
     */
    public DOMMountPoint getMountPoint() {
        return this.mountPoint;
    }

    /**
     * Set {@link LogicalDatastoreType} of data for transaction.
     *
     * @param datastoreType
     *             {@link LogicalDatastoreType}
     */
    public void setLogicalDatastoreType(final LogicalDatastoreType datastoreType) {
        this.configuration = datastoreType;
    }

    /**
     * Get type of data.
     *
     * @return {@link LogicalDatastoreType}
     */
    public LogicalDatastoreType getLogicalDatastoreType() {
        return this.configuration;
    }

    /**
     * Get transaction chain for creating specific transaction for specific
     * operation.
     *
     * @return transaction chain
     */
    public DOMTransactionChain getTransactionChain() {
        return this.transactionChain;
    }

    public TransactionChainHandler getTransactionChainHandler() {
        return transactionChainHandler;
    }
}
