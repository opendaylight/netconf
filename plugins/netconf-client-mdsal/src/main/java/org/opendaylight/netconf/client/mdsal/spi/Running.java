/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.opendaylight.netconf.api.EffectiveOperation.CREATE;
import static org.opendaylight.netconf.api.EffectiveOperation.DELETE;
import static org.opendaylight.netconf.api.EffectiveOperation.MERGE;
import static org.opendaylight.netconf.api.EffectiveOperation.REMOVE;
import static org.opendaylight.netconf.api.EffectiveOperation.REPLACE;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class Running extends AbstractDataStore {
    private final List<AnyxmlNode<DOMSource>> nodes = new ArrayList<>();

    Running(NetconfBaseOps netconfOps, final RemoteDeviceId id,
            boolean rollbackSupport, boolean lockDatastore) {
        super(netconfOps, id, rollbackSupport, lockDatastore);
    }

    ListenableFuture<? extends DOMRpcResult> lock() {
        if (lockDatastore && !lock.getAndSet(true)) {
            return netconfOps.lockRunning(new NetconfRpcFutureCallback("Lock running", id));
        }
        return RPC_SUCCESS;
    }

    @Override
    ListenableFuture<? extends DOMRpcResult> unlock() {
        if (lockDatastore && lock.getAndSet(false)) {
            return netconfOps.unlockRunning(new NetconfRpcFutureCallback("Unlock running", id));
        }
        return RPC_SUCCESS;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> cancel() {
        nodes.clear();
        return unlock();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        final var editConfigStructure = netconfOps.createEditConfigStructure(nodes);
        nodes.clear();
        final var callback = new NetconfRpcFutureCallback("Edit running", id);
        // FIXME: Set default-operation" parameter based on called DataStoreService method.
        //        https://www.rfc-editor.org/rfc/rfc6241#section-7.2
        final var edit = addIntoFutureChain(lock(), () -> netconfOps.editConfigRunning(callback, editConfigStructure,
                rollbackSupport));
        return addUnlock(edit);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(YangInstanceIdentifier path, NormalizedNode data) {
        return editConfig(CREATE, data, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(YangInstanceIdentifier path) {
        return editConfig(DELETE, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(YangInstanceIdentifier path) {
        return editConfig(REMOVE, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(YangInstanceIdentifier path, NormalizedNode data) {
        return editConfig(MERGE, data, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> put(YangInstanceIdentifier path, NormalizedNode data) {
        return editConfig(REPLACE, data, path);
    }

    public ListenableFuture<? extends DOMRpcResult> editConfig(final EffectiveOperation operation,
            final NormalizedNode child, final YangInstanceIdentifier path) {
        nodes.add(netconfOps.createNode(child, operation, path));
        return RPC_SUCCESS;
    }

    public ListenableFuture<? extends DOMRpcResult> editConfig(final EffectiveOperation operation,
            final YangInstanceIdentifier path) {
        nodes.add(netconfOps.createNode(operation, path));
        return RPC_SUCCESS;
    }
}
