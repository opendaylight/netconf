/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.tx;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceDataBroker;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.NetconfDOMTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.impl.Promise.DefaultPromise;

public class NetconfMasterDOMTransaction implements NetconfDOMTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfMasterDOMTransaction.class);

    private final RemoteDeviceId id;
    private final DOMDataBroker delegateBroker;

    private DOMDataReadOnlyTransaction readTx;
    private DOMDataWriteTransaction writeTx;

    public NetconfMasterDOMTransaction(final RemoteDeviceId id,
                                       final SchemaContext schemaContext,
                                       final DOMRpcService rpc,
                                       final NetconfSessionPreferences netconfSessionPreferences) {
        this(id, new NetconfDeviceDataBroker(id, schemaContext, rpc, netconfSessionPreferences));
    }

    public NetconfMasterDOMTransaction(final RemoteDeviceId id, final DOMDataBroker delegateBroker) {
        this.id = id;
        this.delegateBroker = delegateBroker;

        // only ever need 1 readTx since it doesnt need to be closed
        readTx = delegateBroker.newReadOnlyTransaction();
    }

    @Override
    public Future<Optional<NormalizedNodeMessage>> read(final LogicalDatastoreType store,
                                                        final YangInstanceIdentifier path) {
        LOG.trace("{}: Read[{}] {} via NETCONF: {}", id, readTx.getIdentifier(), store, path);

        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> readFuture = readTx.read(store, path);

        final DefaultPromise<Optional<NormalizedNodeMessage>> promise = new DefaultPromise<>();
        Futures.addCallback(readFuture, new FutureCallback<Optional<NormalizedNode<?, ?>>>() {
            @Override
            public void onSuccess(final Optional<NormalizedNode<?, ?>> result) {
                if (!result.isPresent()) {
                    promise.success(Optional.absent());
                } else {
                    promise.success(Optional.of(new NormalizedNodeMessage(path, result.get())));
                }
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                promise.failure(throwable);
            }
        });
        return promise.future();
    }

    @Override
    public Future<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        LOG.trace("{}: Exists[{}] {} via NETCONF: {}", id, readTx.getIdentifier(), store, path);

        final CheckedFuture<Boolean, ReadFailedException> existsFuture = readTx.exists(store, path);

        final DefaultPromise<Boolean> promise = new DefaultPromise<>();
        Futures.addCallback(existsFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(final Boolean result) {
                promise.success(result);
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                promise.failure(throwable);
            }
        });
        return promise.future();
    }

    @Override
    public void put(final LogicalDatastoreType store, final NormalizedNodeMessage data) {
        if (writeTx == null) {
            writeTx = delegateBroker.newWriteOnlyTransaction();
        }

        LOG.trace("{}: Write[{}] {} via NETCONF: {} with payload {}", id, writeTx.getIdentifier(), store,
                data.getIdentifier(), data.getNode());

        writeTx.put(store, data.getIdentifier(), data.getNode());
    }

    @Override
    public void merge(final LogicalDatastoreType store, final NormalizedNodeMessage data) {
        if (writeTx == null) {
            writeTx = delegateBroker.newWriteOnlyTransaction();
        }

        LOG.trace("{}: Merge[{}] {} via NETCONF: {} with payload {}", id, writeTx.getIdentifier(),store,
                data.getIdentifier(), data.getNode());

        writeTx.merge(store, data.getIdentifier(), data.getNode());
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        if (writeTx == null) {
            writeTx = delegateBroker.newWriteOnlyTransaction();
        }

        LOG.trace("{}: Delete[{}} {} via NETCONF: {}", id, writeTx.getIdentifier(), store, path);

        writeTx.delete(store, path);
    }

    @Override
    public boolean cancel() {
        LOG.trace("{}: Cancel[{}} via NETCONF", id, writeTx.getIdentifier());

        return writeTx.cancel();
    }

    @Override
    public Future<Void> submit() {
        LOG.trace("{}: Submit[{}} via NETCONF", id, writeTx.getIdentifier());

        final CheckedFuture<Void, TransactionCommitFailedException> submitFuture = writeTx.submit();
        writeTx = null;

        final DefaultPromise<Void> promise = new DefaultPromise<>();
        Futures.addCallback(submitFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                promise.success(result);
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                promise.failure(throwable);
            }
        });
        return promise.future();
    }

}
