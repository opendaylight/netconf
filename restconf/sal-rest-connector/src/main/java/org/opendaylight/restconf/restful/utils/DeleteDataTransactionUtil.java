/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.common.handlers.api.TransactionChainHandler;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Util class for delete specific data in config DS.
 *
 */
public class DeleteDataTransactionUtil {

    /**
     * @param instanceIdentifier
     *            - {@link InstanceIdentifierContext} of data to delete
     * @param transactionChainHandler
     *            - chain handler
     * @return {@link CheckedFuture}
     */
    public static CheckedFuture<Void, TransactionCommitFailedException> deleteData(
            final InstanceIdentifierContext<?> instanceIdentifier,
            final TransactionChainHandler transactionChainHandler) {
        final DOMTransactionChain transactionChain = transactionChainHandler.getTransactionChain();
        final YangInstanceIdentifier path = instanceIdentifier.getInstanceIdentifier();
        if (instanceIdentifier.getMountPoint() == null) {
            return submitData(transactionChain.newWriteOnlyTransaction(), path);
        } else {
            final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
            final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);

            if (domDataBrokerService.isPresent()) {
                return submitData(transactionChain.newWriteOnlyTransaction(), path);
            }
            final String errMsg = "DOM data broker service isn't available for mount point " + path;
            throw new RestconfDocumentedException(errMsg);
        }
    }

    private static CheckedFuture<Void, TransactionCommitFailedException> submitData(
            final DOMDataWriteTransaction writeTx, final YangInstanceIdentifier path) {
        writeTx.delete(LogicalDatastoreType.CONFIGURATION, path);
        return writeTx.submit();
    }

}
