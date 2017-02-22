/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.tx.proxy;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import com.google.common.base.Optional;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.NetconfDOMReadTransaction;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.impl.Promise;

public class ProxyDOMReadTransaction implements NetconfDOMReadTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyDOMReadTransaction.class);

    private final RemoteDeviceId id;
    private final ActorSystem actorSystem;
    private final ActorRef masterContextRef;

    public ProxyDOMReadTransaction(final RemoteDeviceId id, final ActorSystem actorSystem,
                                   final ActorRef masterContextRef) {
        this.id = id;
        this.actorSystem = actorSystem;
        this.masterContextRef = masterContextRef;
    }

    @Override
    public Future<Optional<NormalizedNodeMessage>> read(final LogicalDatastoreType store,
                                                        final YangInstanceIdentifier path) {

        final Future<Object> readScalaFuture =
                Patterns.ask(masterContextRef, new ReadRequest(store, path), NetconfTopologyUtils.TIMEOUT);

        LOG.trace("{}: Read {} via NETCONF: {}", id, store, path);

        final Promise.DefaultPromise<Optional<NormalizedNodeMessage>> promise = new Promise.DefaultPromise<>();

        readScalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) throws Throwable {
                if (failure != null) { // ask timeout
                    final Exception exception = new DocumentedException(id + ":Master is down. Please try again.",
                            DocumentedException.ErrorType.APPLICATION, DocumentedException.ErrorTag.OPERATION_FAILED,
                            DocumentedException.ErrorSeverity.WARNING);
                    promise.failure(exception);
                    return;
                }
                if (success instanceof Throwable) { // Error sended by master
                    promise.failure((Throwable) success);
                    return;
                }
                if (success instanceof EmptyReadResponse) {
                    promise.success(Optional.absent());
                    return;
                }
                promise.success(Optional.of((NormalizedNodeMessage) success));
            }
        }, actorSystem.dispatcher());

        return promise.future();
    }

    @Override
    public Future<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final Future<Object> existsScalaFuture =
                Patterns.ask(masterContextRef, new ExistsRequest(store, path), NetconfTopologyUtils.TIMEOUT);

        LOG.trace("{}: Exists {} via NETCONF: {}", id, store, path);

        final Promise.DefaultPromise<Boolean> promise = new Promise.DefaultPromise<>();
        existsScalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) throws Throwable {
                if (failure != null) { // ask timeout
                    final Exception exception = new DocumentedException(id + ":Master is down. Please try again.",
                            DocumentedException.ErrorType.APPLICATION, DocumentedException.ErrorTag.OPERATION_FAILED,
                            DocumentedException.ErrorSeverity.WARNING);
                    promise.failure(exception);
                    return;
                }
                if (success instanceof Throwable) {
                    promise.failure((Throwable) success);
                    return;
                }
                promise.success((Boolean) success);
            }
        }, actorSystem.dispatcher());
        return promise.future();
    }

}
