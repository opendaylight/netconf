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
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

class ProxyReadAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyReadAdapter.class);

    private final ActorRef masterTxActor;
    private final RemoteDeviceId id;
    private final ActorSystem actorSystem;
    private final Timeout askTimeout;

    public ProxyReadAdapter(final ActorRef masterTxActor, final RemoteDeviceId id, final ActorSystem actorSystem,
                            final Timeout askTimeout) {
        this.masterTxActor = masterTxActor;
        this.id = id;
        this.actorSystem = actorSystem;
        this.askTimeout = askTimeout;
    }

    public void close() {
        //noop
    }

    public CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(final LogicalDatastoreType store,
                                                                                   final YangInstanceIdentifier path) {
        LOG.trace("{}: Read {} via NETCONF: {}", id, store, path);

        final Future<Object> future = Patterns.ask(masterTxActor, new ReadRequest(store, path), askTimeout);
        final SettableFuture<Optional<NormalizedNode<?, ?>>> settableFuture = SettableFuture.create();
        future.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure,
                                   final Object success) throws Throwable {
                if (failure != null) { // ask timeout
                    final Exception exception = NetconfTopologyUtils.createMasterIsDownException(id);
                    settableFuture.setException(exception);
                    return;
                }
                if (success instanceof Throwable) { // Error sended by master
                    settableFuture.setException((Throwable) success);
                    return;
                }
                if (success instanceof EmptyReadResponse) {
                    settableFuture.set(Optional.absent());
                    return;
                }
                if (success instanceof NormalizedNodeMessage) {
                    final NormalizedNodeMessage data = (NormalizedNodeMessage) success;
                    settableFuture.set(Optional.of(data.getNode()));
                }
            }
        }, actorSystem.dispatcher());
        return Futures.makeChecked(settableFuture, ReadFailedException.MAPPER);
    }

    public CheckedFuture<Boolean, ReadFailedException> exists(final LogicalDatastoreType store,
                                                              final YangInstanceIdentifier path) {
        final Future<Object> existsScalaFuture =
                Patterns.ask(masterTxActor, new ExistsRequest(store, path), askTimeout);

        LOG.trace("{}: Exists {} via NETCONF: {}", id, store, path);

        final SettableFuture<Boolean> settableFuture = SettableFuture.create();
        existsScalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) throws Throwable {
                if (failure != null) { // ask timeout
                    final Exception exception = NetconfTopologyUtils.createMasterIsDownException(id);
                    settableFuture.setException(exception);
                    return;
                }
                if (success instanceof Throwable) {
                    settableFuture.setException((Throwable) success);
                    return;
                }
                settableFuture.set((Boolean) success);
            }
        }, actorSystem.dispatcher());
        return Futures.makeChecked(settableFuture, ReadFailedException.MAPPER);
    }

}
