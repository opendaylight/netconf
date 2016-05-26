/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.transaction;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * This class represent delegation wrapper for transaction variables.
 *
 */
public final class TransactionVarsWrapper {

    private final InstanceIdentifierContext<?> instanceIdentifier;
    private final DOMMountPoint mountPoint;
    private LogicalDatastoreType configuration = null;
    private final DOMMountPointService domMountPointService;
    private final SchemaContext schemaContext;
    private final DOMTransactionChain domTransactionChain;

    /**
     * Set base type of variables, which ones we need for transaction.
     * {@link LogicalDatastoreType} is default set to null (to read all data
     * from ds - config + state).
     *
     * @param instanceIdentifier
     *            - {@link InstanceIdentifierContext} of data for transaction
     * @param mountPoint
     *            - mount point if is presnet
     * @param domTransactionChain
     *            - {@link DOMTransactionChain} for transactions
     * @param domMountPointService
     *            - mount point service
     * @param schemaContext
     *            - {@link SchemaContext}
     */
    public TransactionVarsWrapper(final InstanceIdentifierContext<?> instanceIdentifier, final DOMMountPoint mountPoint,
            final DOMTransactionChain domTransactionChain, final DOMMountPointService domMountPointService,
            final SchemaContext schemaContext) {
        this.instanceIdentifier = instanceIdentifier;
        this.mountPoint = mountPoint;
        this.domTransactionChain = domTransactionChain;
        this.domMountPointService = domMountPointService;
        this.schemaContext = schemaContext;
    }

    /**
     * Get instance identifier of data
     *
     * @return {@link InstanceIdentifierContext}
     */
    public InstanceIdentifierContext<?> getInstanceIdentifier() {
        return this.instanceIdentifier;
    }

    /**
     * Get mount point
     *
     * @return {@link DOMMountPoint}
     */
    public DOMMountPoint getMountPoint() {
        return this.mountPoint;
    }

    /**
     * Set {@link LogicalDatastoreType} of data for transaction.
     *
     * @param configuration
     *            - {@link LogicalDatastoreType}
     */
    public void setLogicalDatastoreType(final LogicalDatastoreType configuration) {
        this.configuration = configuration;

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
     * Get mount point service
     *
     * @return {@link DOMMountPointService}
     */
    public DOMMountPointService getDomMountPointService() {
        return this.domMountPointService;
    }

    /**
     * Get schema context of data
     *
     * @return {@link SchemaContext}
     */
    public SchemaContext getSchemaContext() {
        return this.schemaContext;
    }

    /**
     * Get transaction chain
     *
     * @return {@link DOMTransactionChain}
     */
    public DOMTransactionChain getDomTransactionChain() {
        return this.domTransactionChain;
    }
}
