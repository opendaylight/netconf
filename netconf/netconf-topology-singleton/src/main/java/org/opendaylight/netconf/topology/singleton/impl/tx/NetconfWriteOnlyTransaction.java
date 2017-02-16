/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.tx;

import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import com.google.common.base.Function;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.NetconfDOMTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

public class NetconfWriteOnlyTransaction implements DOMDataWriteTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfWriteOnlyTransaction.class);

    private final RemoteDeviceId id;
    private final NetconfDOMTransaction delegate;
    private final ActorSystem actorSystem;

    public NetconfWriteOnlyTransaction(final RemoteDeviceId id,
                                       final ActorSystem actorSystem,
                                       final NetconfDOMTransaction delegate) {
        this.id = id;
        this.delegate = delegate;
        this.actorSystem = actorSystem;

        this.delegate.openTransaction();
    }

    @Override
    public void put(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                    final NormalizedNode<?,?> data) {
        LOG.trace("{}: Write {} via NETCONF: {} with payload {}", id, store, path, data);

        delegate.put(store, new NormalizedNodeMessage(path, data));
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                      final NormalizedNode<?,?> data) {
        LOG.trace("{}: Merge {} via NETCONF: {} with payload {}", id, store, path, data);

        delegate.merge(store, new NormalizedNodeMessage(path, data));
    }

    @Override
    public boolean cancel() {
        LOG.trace("{}: Cancel", id);

        return delegate.cancel();
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        LOG.trace("{}: Delete {} via NETCONF: {}", id, store, path);

        delegate.delete(store, path);
    }

    @Override
    public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        LOG.trace("{}: Submit", id);

        final Future<Void> submit = delegate.submit();
        final SettableFuture<Void> settFuture = SettableFuture.create();
        final CheckedFuture<Void, TransactionCommitFailedException> checkedFuture;
        checkedFuture = Futures.makeChecked(settFuture, new Function<Exception, TransactionCommitFailedException>() {
            @Nullable
            @Override
            public TransactionCommitFailedException apply(Exception input) {
                return new TransactionCommitFailedException("Transaction commit failed", input);
            }
        });
        submit.onComplete(new OnComplete<Void>() {
            @Override
            public void onComplete(Throwable throwable, Void object) throws Throwable {
                if (throwable == null) {
                    settFuture.set(object);
                } else {
                    settFuture.setException(throwable);
                }
            }
        }, actorSystem.dispatcher());
        return checkedFuture;
    }

    @Override
    public ListenableFuture<RpcResult<TransactionStatus>> commit() {
        LOG.trace("{}: Commit", id);

        final Future<Void> commit = delegate.submit();
        final SettableFuture<RpcResult<TransactionStatus>> settFuture = SettableFuture.create();
        commit.onComplete(new OnComplete<Void>() {
            @Override
            public void onComplete(final Throwable throwable, final Void result) throws Throwable {
                if (throwable == null) {
                    TransactionStatus status = TransactionStatus.SUBMITED;
                    RpcResult<TransactionStatus> rpcResult = RpcResultBuilder.success(status).build();
                    settFuture.set(rpcResult);
                } else {
                    settFuture.setException(throwable);
                }
            }
        }, actorSystem.dispatcher());
        return settFuture;
    }

    @Override
    public Object getIdentifier() {
        return this;
    }
}
