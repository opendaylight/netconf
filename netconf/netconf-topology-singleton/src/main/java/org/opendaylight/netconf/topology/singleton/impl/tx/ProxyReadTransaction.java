/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
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
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
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

/**
 * ProxyReadTransaction uses provided {@link ActorRef} to delegate method calls to master
 * {@link org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActor}.
 */
public class ProxyReadTransaction implements DOMDataReadOnlyTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyReadTransaction.class);
    private static final Timeout TIMEOUT = NetconfTopologyUtils.TIMEOUT;

    private final ActorRef masterTxActor;
    private final RemoteDeviceId id;
    private final ActorSystem actorSystem;

    /**
     * @param masterTxActor {@link org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActor} ref
     * @param id            device id
     * @param actorSystem   system
     */
    public ProxyReadTransaction(final ActorRef masterTxActor, final RemoteDeviceId id,
                                final ActorSystem actorSystem) {
        this.masterTxActor = masterTxActor;
        this.id = id;
        this.actorSystem = actorSystem;
    }

    @Override
    public void close() {
        //noop
    }

    @Override
    public CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        LOG.trace("{}: Read {} via NETCONF: {}", id, store, path);

        final Future<Object> future = Patterns.ask(masterTxActor, new ReadRequest(store, path), TIMEOUT);
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

    @Override
    public CheckedFuture<Boolean, ReadFailedException> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final Future<Object> existsScalaFuture =
                Patterns.ask(masterTxActor, new ExistsRequest(store, path), NetconfTopologyUtils.TIMEOUT);

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


    @Override
    public Object getIdentifier() {
        return this;
    }
}
