/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfRpcFutureCallback;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tx implementation for netconf devices that support only writable-running with no candidate.
 * The sequence goes as:
 * <ol>
 *   <li>Lock running datastore on tx construction
 *     <ul>
 *       <li> Lock has to succeed, if it does not, transaction is failed</li>
 *     </ul>
 *   </li>
 *   <li>Edit-config in running N times
 *     <ul>
 *       <li>If any issue occurs during edit, datastore is unlocked and an exception is thrown</li>
 *     </ul>
 *   </li>
 *   <li>Unlock running datastore on tx commit</li>
 * </ol>
 */
public class WriteRunningTx extends AbstractWriteTx {

    private static final Logger LOG  = LoggerFactory.getLogger(WriteRunningTx.class);
    private final List<Change> changes = new ArrayList<>();

    public WriteRunningTx(final RemoteDeviceId id, final NetconfBaseOps netOps,
                          final boolean rollbackSupport) {
        this(id, netOps, rollbackSupport, true);
    }

    public WriteRunningTx(RemoteDeviceId id, NetconfBaseOps netconfOps, boolean rollbackSupport,
            boolean isLockAllowed) {
        super(id, netconfOps, rollbackSupport, isLockAllowed);
    }

    @Override
    protected synchronized void init() {
        lock();
    }

    private void lock() {
        if (isLockAllowed) {
            resultsFutures.add(netOps.lockRunning(new NetconfRpcFutureCallback("Lock running", id)));
        } else {
            LOG.trace("Lock is not allowed: {}", id);
        }
    }

    @Override
    protected void cleanup() {
        unlock();
    }

    @Override
    public synchronized ListenableFuture<RpcResult<Void>> performCommit() {
        for (final Change change : changes) {
            resultsFutures.add(change.execute(id, netOps, rollbackSupport));
        }
        unlock();
        return resultsToTxStatus();
    }

    @Override
    protected void editConfig(final YangInstanceIdentifier path,
                              final Optional<NormalizedNode<?, ?>> data,
                              final DataContainerChild<?, ?> editStructure,
                              final Optional<ModifyAction> defaultOperation,
                              final String operation) {
        changes.add(new Change(editStructure, defaultOperation));
    }

    private void unlock() {
        if (isLockAllowed) {
            netOps.unlockRunning(new NetconfRpcFutureCallback("Unlock running", id));
        } else {
            LOG.trace("Unlock is not allowed: {}", id);
        }
    }

    private static final class Change {

        private final DataContainerChild<?, ?> editStructure;
        private final Optional<ModifyAction> defaultOperation;

        Change(final DataContainerChild<?, ?> editStructure, final Optional<ModifyAction> defaultOperation) {
            this.editStructure = editStructure;
            this.defaultOperation = defaultOperation;
        }

        private ListenableFuture<DOMRpcResult> execute(final RemoteDeviceId id, final NetconfBaseOps netOps,
                                                       final boolean rollbackSupport) {
            final NetconfRpcFutureCallback editConfigCallback = new NetconfRpcFutureCallback("Edit running", id);
            if (defaultOperation.isPresent()) {
                return netOps.editConfigRunning(editConfigCallback, editStructure, defaultOperation.get(),
                    rollbackSupport);
            } else {
                return netOps.editConfigRunning(editConfigCallback, editStructure, rollbackSupport);
            }
        }
    }
}
