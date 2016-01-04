/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.pipeline.tx;

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
import org.opendaylight.netconf.topology.pipeline.ProxyNetconfDeviceDataBroker;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import scala.concurrent.Future;

public class ProxyWriteOnlyTransaction implements DOMDataWriteTransaction {

    private final ProxyNetconfDeviceDataBroker delegate;
    private final ActorSystem actorSystem;

    public ProxyWriteOnlyTransaction(ActorSystem actorSystem, final ProxyNetconfDeviceDataBroker delegate) {
        this.delegate = delegate;
        this.actorSystem = actorSystem;
    }

    @Override
    public void put (final LogicalDatastoreType store, final YangInstanceIdentifier path, final NormalizedNode < ?,?>data){
        delegate.put(store, new NormalizedNodeMessage(path, data));
    }

    @Override
    public void merge (final LogicalDatastoreType store, final YangInstanceIdentifier path, final NormalizedNode < ?,?>data){
        delegate.merge(store, new NormalizedNodeMessage(path, data));
    }

    @Override
    public boolean cancel () {
        return delegate.cancel();
    }

    @Override
    public void delete (final LogicalDatastoreType store, final YangInstanceIdentifier path){
        delegate.delete(store, path);
    }

    @Override
    public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        final Future<Void> submit = delegate.submit();
        final SettableFuture<Void> settableFuture = SettableFuture.create();
        final CheckedFuture<Void, TransactionCommitFailedException> checkedFuture = Futures.makeChecked(settableFuture, new Function<Exception, TransactionCommitFailedException>() {
            @Nullable
            @Override
            public TransactionCommitFailedException apply(Exception input) {
                return new TransactionCommitFailedException("Transaction commit failed", input);
            }
        });
        submit.onComplete(new OnComplete<Void>() {
            @Override
            public void onComplete(Throwable throwable, Void aVoid) throws Throwable {
                if (throwable == null) {
                    settableFuture.set(aVoid);
                } else {
                    settableFuture.setException(throwable);
                }
            }
        }, actorSystem.dispatcher());
        return checkedFuture;
    }

    @Override
    public ListenableFuture<RpcResult<TransactionStatus>> commit () {
        final Future<RpcResult<TransactionStatus>> commit = delegate.commit();
        final SettableFuture<RpcResult<TransactionStatus>> settableFuture = SettableFuture.create();
        commit.onComplete(new OnComplete<RpcResult<TransactionStatus>>() {
            @Override
            public void onComplete(Throwable throwable, RpcResult<TransactionStatus> transactionStatusRpcResult) throws Throwable {
                if (throwable == null) {
                    settableFuture.set(transactionStatusRpcResult);
                } else {
                    settableFuture.setException(throwable);
                }
            }
        }, actorSystem.dispatcher());
        return settableFuture;
    }

    @Override
    public Object getIdentifier () {
        return this;
    }
}
