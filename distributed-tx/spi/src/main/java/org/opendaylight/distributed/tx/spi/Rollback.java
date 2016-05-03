/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.spi;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Map;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * APIs for the rollback functionality to set the data to all nodes to pre-dtx state.
 */
public interface Rollback {
    /**
     * Rollback all the data in the nodeCache.
     *
     * @param nodeCache contains all the pre-dtx data of each node in the transaction.
     * @param nodeTx contains all the ReadWriteTransaction of each node in the transaction.
     *
     * @return ListenableFuture indicating the result of rollback.
     * <ul>
     * <li> Exceptions should be set to the future. </li>
     * </ul>
     */
    ListenableFuture<Void> rollback(@Nonnull Map<InstanceIdentifier<?>, ? extends TxCache> nodeCache,
        @Nonnull Map<InstanceIdentifier<?>, ? extends ReadWriteTransaction> nodeTx);
}
