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

public final class TransactionNode {

    private final InstanceIdentifierContext<?> instanceIdentifier;
    private final DOMMountPoint mountPoint;
    private LogicalDatastoreType configuration = null;
    private final DOMMountPointService domMountPointService;
    private final SchemaContext schemaContext;
    private final DOMTransactionChain domTransactionChain;

    public TransactionNode(final InstanceIdentifierContext<?> instanceIdentifier, final DOMMountPoint mountPoint,
            final DOMTransactionChain domTransactionChain, final DOMMountPointService domMountPointService,
            final SchemaContext schemaContext) {
        this.instanceIdentifier = instanceIdentifier;
        this.mountPoint = mountPoint;
        this.domTransactionChain = domTransactionChain;
        this.domMountPointService = domMountPointService;
        this.schemaContext = schemaContext;
    }

    public InstanceIdentifierContext<?> getInstanceIdentifier() {
        return this.instanceIdentifier;
    }

    public DOMMountPoint getMountPoint() {
        return this.mountPoint;
    }

    public void setLogicalDatastoreType(final LogicalDatastoreType configuration) {
        this.configuration = configuration;

    }

    public LogicalDatastoreType getLogicalDatastoreType() {
        return this.configuration;
    }

    public DOMMountPointService getDomMountPointService() {
        return this.domMountPointService;
    }

    public SchemaContext getSchemaContext() {
        return this.schemaContext;
    }

    public DOMTransactionChain getDomTransactionChain() {
        return domTransactionChain;
    }
}
