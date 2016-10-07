/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
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
import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.singleton.api.NetconfDOMTransaction;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.impl.Promise.DefaultPromise;


public class NetconfProxyDOMTransaction implements NetconfDOMTransaction {

    private final ActorSystem actorSystem;
    private final ActorRef masterContextRef;

    public NetconfProxyDOMTransaction(final ActorSystem actorSystem, final ActorRef masterContextRef) {
        this.actorSystem = actorSystem;
        this.masterContextRef = masterContextRef;
    }

    @Override
    public Future<Optional<NormalizedNodeMessage>> read(final LogicalDatastoreType store,
                                                        final YangInstanceIdentifier path) {

        final Future<Object> readScalaFuture =
                Patterns.ask(masterContextRef, new ReadRequest(store, path), NetconfTopologyUtils.TIMEOUT);

        final DefaultPromise<Optional<NormalizedNodeMessage>> promise = new DefaultPromise<>();

        readScalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) throws Throwable {
                if (failure != null) {
                    promise.failure(failure);
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

        final DefaultPromise<Boolean> promise = new DefaultPromise<>();
        existsScalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) throws Throwable {
                if (failure != null) {
                    promise.failure(failure);
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

    @Override
    public void put(final LogicalDatastoreType store, final NormalizedNodeMessage data) {
        masterContextRef.tell(new PutRequest(store, data), ActorRef.noSender());
    }

    @Override
    public void merge(final LogicalDatastoreType store, final NormalizedNodeMessage data) {
        masterContextRef.tell(new MergeRequest(store, data), ActorRef.noSender());
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        masterContextRef.tell(new DeleteRequest(store, path), ActorRef.noSender());
    }

    @Override
    public boolean cancel() {
        final Future<Object> cancelScalaFuture =
                Patterns.ask(masterContextRef, new CancelRequest(), NetconfTopologyUtils.TIMEOUT);
        try {
            // here must be Await because AsyncWriteTransaction do not return future
            return (boolean) Await.result(cancelScalaFuture, NetconfTopologyUtils.TIMEOUT.duration());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Future<Void> submit() {
        final Future<Object> submitScalaFuture =
                Patterns.ask(masterContextRef, new SubmitRequest(), NetconfTopologyUtils.TIMEOUT);

        final DefaultPromise<Void> promise = new DefaultPromise<>();

        submitScalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) throws Throwable {
                if (failure != null) {
                    promise.failure(failure);
                    return;
                }
                if (success instanceof Throwable) {
                    promise.failure((Throwable) success);
                } else {
                    promise.success(null);
                }
            }
        }, actorSystem.dispatcher());

        return promise.future();
    }

}
