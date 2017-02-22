/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.tx.master;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceDataBroker;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.NetconfDOMReadTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.impl.Promise;

public class MasterDOMReadTransaction implements NetconfDOMReadTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(MasterDOMReadTransaction.class);

    private final RemoteDeviceId id;

    private final DOMDataReadOnlyTransaction readTx;

    public MasterDOMReadTransaction(final RemoteDeviceId id,
                                    final SchemaContext schemaContext,
                                    final DOMRpcService rpc,
                                    final NetconfSessionPreferences netconfSessionPreferences) {
        this(id, new NetconfDeviceDataBroker(id, schemaContext, rpc, netconfSessionPreferences));
    }

    public MasterDOMReadTransaction(final RemoteDeviceId id, final DOMDataBroker delegateBroker) {
        this.id = id;

        // only ever need 1 readTx since it doesnt need to be closed
        readTx = delegateBroker.newReadOnlyTransaction();
    }

    @Override
    public Future<Optional<NormalizedNodeMessage>> read(final LogicalDatastoreType store,
                                                        final YangInstanceIdentifier path) {
        LOG.trace("{}: Read[{}] {} via NETCONF: {}", id, readTx.getIdentifier(), store, path);

        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> readFuture = readTx.read(store, path);

        final Promise.DefaultPromise<Optional<NormalizedNodeMessage>> promise = new Promise.DefaultPromise<>();
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

        final Promise.DefaultPromise<Boolean> promise = new Promise.DefaultPromise<>();
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

}
