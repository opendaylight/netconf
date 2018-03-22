/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.tx;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * ProxyReadWriteTransaction uses provided {@link ActorRef} to delegate method calls to master
 * {@link org.opendaylight.netconf.topology.singleton.impl.actors.ReadWriteTransactionActor}.
 */
public class ProxyReadWriteTransaction implements DOMDataReadWriteTransaction {

    private final ProxyReadAdapter delegateRead;
    private final ProxyWriteAdapter delegateWrite;

    /**
     * Constructor for {@code ProxyReadWriteTransaction}.
     *
     * @param masterTxActor
     * {@link org.opendaylight.netconf.topology.singleton.impl.actors.ReadWriteTransactionActor} ref
     * @param id            device id
     * @param actorSystem   system
     * @param askTimeout    timeout
     */
    public ProxyReadWriteTransaction(final ActorRef masterTxActor, final RemoteDeviceId id,
                                     final ActorSystem actorSystem, final Timeout askTimeout) {
        delegateRead = new ProxyReadAdapter(masterTxActor, id, actorSystem, askTimeout);
        delegateWrite = new ProxyWriteAdapter(masterTxActor, id, actorSystem, askTimeout);
    }

    @Override
    public boolean cancel() {
        return delegateWrite.cancel();
    }

    @Override
    public CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(final LogicalDatastoreType store,
                                                                                   final YangInstanceIdentifier path) {
        return delegateRead.read(store, path);
    }

    @Override
    public CheckedFuture<Boolean, ReadFailedException> exists(final LogicalDatastoreType store,
                                                              final YangInstanceIdentifier path) {
        return delegateRead.exists(store, path);
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        delegateWrite.delete(store, path);
    }

    @Override
    public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        return delegateWrite.submit(getIdentifier());
    }

    @Override
    public void put(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                    final NormalizedNode<?, ?> data) {
        delegateWrite.put(store, path, data, getIdentifier());
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                      final NormalizedNode<?, ?> data) {
        delegateWrite.merge(store, path, data, getIdentifier());
    }

    @Override
    public Object getIdentifier() {
        return this;
    }
}
