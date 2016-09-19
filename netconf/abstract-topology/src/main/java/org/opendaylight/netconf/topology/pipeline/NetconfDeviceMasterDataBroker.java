/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.pipeline;

import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceDataBroker;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.ReadWriteTx;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.pipeline.tx.ProxyReadOnlyTransaction;
import org.opendaylight.netconf.topology.pipeline.tx.ProxyWriteOnlyTransaction;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import scala.concurrent.Future;
import scala.concurrent.impl.Promise.DefaultPromise;

public class NetconfDeviceMasterDataBroker implements ProxyNetconfDeviceDataBroker {

    private final RemoteDeviceId id;

    private final NetconfDeviceDataBroker delegateBroker;
    private final ActorSystem actorSystem;

    private DOMDataReadOnlyTransaction readTx;
    private DOMDataWriteTransaction writeTx;

    public NetconfDeviceMasterDataBroker(final ActorSystem actorSystem, final RemoteDeviceId id,
                                         final SchemaContext schemaContext, final DOMRpcService rpc,
                                         final NetconfSessionPreferences netconfSessionPreferences) {
        this.id = id;
        delegateBroker = new NetconfDeviceDataBroker(id, schemaContext, rpc, netconfSessionPreferences);
        this.actorSystem = actorSystem;

        // only ever need 1 readTx since it doesnt need to be closed
        readTx = delegateBroker.newReadOnlyTransaction();
    }

    @Override
    public DOMDataReadOnlyTransaction newReadOnlyTransaction() {
        return new ProxyReadOnlyTransaction(actorSystem, id, TypedActor.<NetconfDeviceMasterDataBroker>self());
    }

    @Override
    public DOMDataReadWriteTransaction newReadWriteTransaction() {
        return new ReadWriteTx(new ProxyReadOnlyTransaction(actorSystem, id, TypedActor.<NetconfDeviceMasterDataBroker>self()),
                newWriteOnlyTransaction());
    }

    @Override
    public DOMDataWriteTransaction newWriteOnlyTransaction() {
        writeTx = delegateBroker.newWriteOnlyTransaction();
        return new ProxyWriteOnlyTransaction(actorSystem, TypedActor.<NetconfDeviceMasterDataBroker>self());
    }

    @Override
    public ListenerRegistration<DOMDataChangeListener> registerDataChangeListener(LogicalDatastoreType store, YangInstanceIdentifier path, DOMDataChangeListener listener, DataChangeScope triggeringScope) {
        throw new UnsupportedOperationException(id + ": Data change listeners not supported for netconf mount point");
    }

    @Override
    public DOMTransactionChain createTransactionChain(TransactionChainListener listener) {
        throw new UnsupportedOperationException(id + ": Transaction chains not supported for netconf mount point");
    }

    @Nonnull
    @Override
    public Map<Class<? extends DOMDataBrokerExtension>, DOMDataBrokerExtension> getSupportedExtensions() {
        return Collections.emptyMap();
    }

    @Override
    public Future<Optional<NormalizedNodeMessage>> read(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> readFuture = readTx.read(store, path);

        final DefaultPromise<Optional<NormalizedNodeMessage>> promise = new DefaultPromise<>();
        Futures.addCallback(readFuture, new FutureCallback<Optional<NormalizedNode<?, ?>>>() {
            @Override
            public void onSuccess(Optional<NormalizedNode<?, ?>> result) {
                if (!result.isPresent()) {
                    promise.success(Optional.<NormalizedNodeMessage>absent());
                } else {
                    promise.success(Optional.of(new NormalizedNodeMessage(path, result.get())));
                }
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
            }
        });
        return promise.future();
    }

    @Override
    public Future<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final CheckedFuture<Boolean, ReadFailedException> existsFuture = readTx.exists(store, path);

        final DefaultPromise<Boolean> promise = new DefaultPromise<>();
        Futures.addCallback(existsFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                promise.success(result);
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
            }
        });
        return promise.future();
    }

    @Override
    public void put(final LogicalDatastoreType store, final NormalizedNodeMessage data) {
        if (writeTx == null) {
            writeTx = delegateBroker.newWriteOnlyTransaction();
        }
        writeTx.put(store, data.getIdentifier(), data.getNode());
    }

    @Override
    public void merge(final LogicalDatastoreType store, final NormalizedNodeMessage data) {
        if (writeTx == null) {
            writeTx = delegateBroker.newWriteOnlyTransaction();
        }
        writeTx.merge(store, data.getIdentifier(), data.getNode());
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        if (writeTx == null) {
            writeTx = delegateBroker.newWriteOnlyTransaction();
        }
        writeTx.delete(store, path);
    }

    @Override
    public boolean cancel() {
        return writeTx.cancel();
    }

    @Override
    public Future<Void> submit() {
        final CheckedFuture<Void, TransactionCommitFailedException> submitFuture = writeTx.submit();
        final DefaultPromise<Void> promise = new DefaultPromise<>();
        Futures.addCallback(submitFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                promise.success(result);
                writeTx = null;
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
                writeTx = null;
            }
        });
        return promise.future();
    }

    @Override
    @Deprecated
    public Future<RpcResult<TransactionStatus>> commit() {
        final ListenableFuture<RpcResult<TransactionStatus>> commitFuture = writeTx.commit();
        final DefaultPromise<RpcResult<TransactionStatus>> promise = new DefaultPromise<>();
        Futures.addCallback(commitFuture, new FutureCallback<RpcResult<TransactionStatus>>() {
            @Override
            public void onSuccess(RpcResult<TransactionStatus> result) {
                promise.success(result);
                writeTx = null;
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
                writeTx = null;
            }
        });
        return promise.future();
    }

}
