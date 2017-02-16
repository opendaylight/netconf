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
import com.google.common.base.Throwables;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.NetconfDOMTransaction;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.OpenTransaction;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitFailedReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.impl.Promise.DefaultPromise;


public class NetconfProxyDOMTransaction implements NetconfDOMTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfProxyDOMTransaction.class);

    private final RemoteDeviceId id;
    private final ActorSystem actorSystem;
    private final ActorRef masterContextRef;

    public NetconfProxyDOMTransaction(final RemoteDeviceId id,
                                      final ActorSystem actorSystem,
                                      final ActorRef masterContextRef) {
        this.id = id;
        this.actorSystem = actorSystem;
        this.masterContextRef = masterContextRef;
    }

    @Override
    public void openTransaction() {
        // TODO we can do some checking for already opened transaction also
        // here in this class. We can track open transaction at least for this
        // node.
        LOG.debug("{}: Requesting leader {} to open new transaction", id, masterContextRef);
        final Future<Object> openTxFuture =
                Patterns.ask(masterContextRef, new OpenTransaction(), NetconfTopologyUtils.TIMEOUT);
        try {
            // we have to wait here so we can see if tx can be opened
            Await.result(openTxFuture, NetconfTopologyUtils.TIMEOUT.duration());
            LOG.debug("{}: New transaction opened successfully", id);
        } catch (final Exception e) {
            LOG.error("{}: Failed to open new transaction", id, e);
            Throwables.propagate(e);
        }
    }

    @Override
    public Future<Optional<NormalizedNodeMessage>> read(final LogicalDatastoreType store,
                                                        final YangInstanceIdentifier path) {

        final Future<Object> readScalaFuture =
                Patterns.ask(masterContextRef, new ReadRequest(store, path), NetconfTopologyUtils.TIMEOUT);

        LOG.trace("{}: Read {} via NETCONF: {}", id, store, path);

        final DefaultPromise<Optional<NormalizedNodeMessage>> promise = new DefaultPromise<>();

        readScalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) throws Throwable {
                if (failure != null) { // ask timeout
                    Exception exception = new DocumentedException(id + ":Master is down. Please try again.",
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

        final DefaultPromise<Boolean> promise = new DefaultPromise<>();
        existsScalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) throws Throwable {
                if (failure != null) { // ask timeout
                    Exception exception = new DocumentedException(id + ":Master is down. Please try again.",
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

    @Override
    public void put(final LogicalDatastoreType store, final NormalizedNodeMessage data) {
        LOG.trace("{}: Write {} via NETCONF: {} with payload {}", id, store, data.getIdentifier(), data.getNode());

        masterContextRef.tell(new PutRequest(store, data), ActorRef.noSender());

    }

    @Override
    public void merge(final LogicalDatastoreType store, final NormalizedNodeMessage data) {
        LOG.trace("{}: Merge {} via NETCONF: {} with payload {}", id, store, data.getIdentifier(), data.getNode());

        masterContextRef.tell(new MergeRequest(store, data), ActorRef.noSender());
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        LOG.trace("{}: Delete {} via NETCONF: {}", id, store, path);

        masterContextRef.tell(new DeleteRequest(store, path), ActorRef.noSender());
    }

    @Override
    public boolean cancel() {
        final Future<Object> cancelScalaFuture =
                Patterns.ask(masterContextRef, new CancelRequest(), NetconfTopologyUtils.TIMEOUT);

        LOG.trace("{}: Cancel {} via NETCONF", id);

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

        LOG.trace("{}: Submit {} via NETCONF", id);

        final DefaultPromise<Void> promise = new DefaultPromise<>();

        submitScalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) throws Throwable {
                if (failure != null) { // ask timeout
                    Exception exception = new DocumentedException(id + ":Master is down. Please try again.",
                            DocumentedException.ErrorType.APPLICATION, DocumentedException.ErrorTag.OPERATION_FAILED,
                            DocumentedException.ErrorSeverity.WARNING);
                    promise.failure(exception);
                    return;
                }
                if (success instanceof Throwable) {
                    promise.failure((Throwable) success);
                } else {
                    if (success instanceof SubmitFailedReply) {
                        LOG.error("{}: Transaction was not submitted because already closed.", id);
                    }
                    promise.success(null);
                }
            }
        }, actorSystem.dispatcher());

        return promise.future();
    }

}
