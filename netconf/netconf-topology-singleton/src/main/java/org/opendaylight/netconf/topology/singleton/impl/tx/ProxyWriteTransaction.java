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
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.MappingCheckedFuture;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.util.concurrent.ExceptionMapper;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * ProxyWriteTransaction uses provided {@link ActorRef} to delegate method calls to master
 * {@link org.opendaylight.netconf.topology.singleton.impl.actors.WriteTransactionActor}.
 */
public class ProxyWriteTransaction implements DOMDataWriteTransaction {

    private final ProxyWriteAdapter proxyWriteAdapter;

    /**
     * Constructor for {@code ProxyWriteTransaction}.
     *
     * @param masterTxActor {@link org.opendaylight.netconf.topology.singleton.impl.actors.WriteTransactionActor} ref
     * @param id            device id
     * @param actorSystem   system
     * @param askTimeout    timeout
     */
    public ProxyWriteTransaction(final ActorRef masterTxActor, final RemoteDeviceId id, final ActorSystem actorSystem,
                                 final Timeout askTimeout) {
        proxyWriteAdapter = new ProxyWriteAdapter(masterTxActor, id, actorSystem, askTimeout);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public boolean cancel() {
        return proxyWriteAdapter.cancel();
    }

    @Override
    public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        return MappingCheckedFuture.create(commit().transform(ignored -> null, MoreExecutors.directExecutor()),
            new ExceptionMapper<TransactionCommitFailedException>("commit", TransactionCommitFailedException.class) {
                @Override
                protected TransactionCommitFailedException newWithCause(String message, Throwable cause) {
                    return new TransactionCommitFailedException(message, cause);
                }
            });
    }

    @Override
    public @NonNull FluentFuture<? extends @NonNull CommitInfo> commit() {
        return proxyWriteAdapter.commit(getIdentifier());
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier identifier) {
        proxyWriteAdapter.delete(store, identifier);
    }

    @Override
    public void put(final LogicalDatastoreType store, final YangInstanceIdentifier identifier,
                    final NormalizedNode<?, ?> data) {
        proxyWriteAdapter.put(store, identifier, data, getIdentifier());
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier identifier,
                      final NormalizedNode<?, ?> data) {
        proxyWriteAdapter.merge(store, identifier, data, getIdentifier());
    }

    @Override
    public Object getIdentifier() {
        return this;
    }
}
