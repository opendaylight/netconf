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
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitFailedReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;
import scala.concurrent.Future;

public class ProxyWriteAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyWriteAdapter.class);

    private final ActorRef masterTxActor;
    private final RemoteDeviceId id;
    private final ActorSystem actorSystem;
    private final AtomicBoolean opened = new AtomicBoolean(true);
    private final Timeout askTimeout;

    public ProxyWriteAdapter(final ActorRef masterTxActor, final RemoteDeviceId id, final ActorSystem actorSystem,
                             final Timeout askTimeout) {
        this.masterTxActor = masterTxActor;
        this.id = id;
        this.actorSystem = actorSystem;
        this.askTimeout = askTimeout;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public boolean cancel() {
        if (!opened.compareAndSet(true, false)) {
            return false;
        }
        final Future<Object> cancelScalaFuture =
                Patterns.ask(masterTxActor, new CancelRequest(), askTimeout);

        LOG.trace("{}: Cancel {} via NETCONF", id);

        try {
            // here must be Await because AsyncWriteTransaction do not return future
            return (boolean) Await.result(cancelScalaFuture, askTimeout.duration());
        } catch (final Exception e) {
            return false;
        }
    }

    public @NonNull FluentFuture<? extends @NonNull CommitInfo> commit(final Object identifier) {
        if (!opened.compareAndSet(true, false)) {
            throw new IllegalStateException(id + ": Transaction" + identifier + " is closed");
        }
        final Future<Object> submitScalaFuture =
                Patterns.ask(masterTxActor, new SubmitRequest(), askTimeout);

        LOG.trace("{}: Commit {} via NETCONF", id);

        final SettableFuture<CommitInfo> settableFuture = SettableFuture.create();
        submitScalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) throws Throwable {
                if (failure != null) { // ask timeout
                    settableFuture.setException(newTransactionCommitFailedException(
                            NetconfTopologyUtils.createMasterIsDownException(id), identifier));
                    return;
                }
                if (success instanceof Throwable) {
                    settableFuture.setException(newTransactionCommitFailedException((Throwable) success, identifier));
                } else {
                    if (success instanceof SubmitFailedReply) {
                        LOG.error("{}: Transaction was not submitted because already closed.", id);
                        settableFuture.setException(newTransactionCommitFailedException(
                                ((SubmitFailedReply) success).getThrowable(), identifier));
                        return;
                    }

                    settableFuture.set(null);
                }
            }
        }, actorSystem.dispatcher());

        return FluentFuture.from(settableFuture);
    }

    private static TransactionCommitFailedException newTransactionCommitFailedException(final Throwable failure,
            final Object identifier) {
        return new TransactionCommitFailedException(
                String.format("Commit of transaction %s failed", identifier), failure);
    }

    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier identifier) {
        Preconditions.checkState(opened.get(), "%s: Transaction was closed %s", id, identifier);
        LOG.trace("{}: Delete {} via NETCONF: {}", id, store, identifier);
        masterTxActor.tell(new DeleteRequest(store, identifier), ActorRef.noSender());
    }

    public void put(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                    final NormalizedNode<?, ?> data, final Object identifier) {
        Preconditions.checkState(opened.get(), "%s: Transaction was closed %s", id, identifier);
        final NormalizedNodeMessage msg = new NormalizedNodeMessage(path, data);
        LOG.trace("{}: Put {} via NETCONF: {} with payload {}", id, store, path, data);
        masterTxActor.tell(new PutRequest(store, msg), ActorRef.noSender());
    }

    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                      final NormalizedNode<?, ?> data, final Object identifier) {
        Preconditions.checkState(opened.get(), "%s: Transaction was closed %s", id, identifier);
        final NormalizedNodeMessage msg = new NormalizedNodeMessage(path, data);
        LOG.trace("{}: Merge {} via NETCONF: {} with payload {}", id, store, path, data);
        masterTxActor.tell(new MergeRequest(store, msg), ActorRef.noSender());
    }

}
