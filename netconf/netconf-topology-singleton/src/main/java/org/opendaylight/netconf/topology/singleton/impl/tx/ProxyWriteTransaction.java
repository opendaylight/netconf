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
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitFailedReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;
import scala.concurrent.Future;

/**
 * ProxyWriteTransaction uses provided {@link ActorRef} to delegate method calls to master
 * {@link org.opendaylight.netconf.topology.singleton.impl.actors.WriteTransactionActor}.
 */
public class ProxyWriteTransaction implements DOMDataWriteTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyWriteTransaction.class);

    private final ActorRef masterTxActor;
    private final RemoteDeviceId id;
    private final ActorSystem actorSystem;
    private final AtomicBoolean opened = new AtomicBoolean(true);

    /**
     * @param masterTxActor {@link org.opendaylight.netconf.topology.singleton.impl.actors.WriteTransactionActor} ref
     * @param id            device id
     * @param actorSystem   system
     */
    public ProxyWriteTransaction(final ActorRef masterTxActor, final RemoteDeviceId id,
                                 final ActorSystem actorSystem) {
        this.masterTxActor = masterTxActor;
        this.id = id;
        this.actorSystem = actorSystem;
    }

    @Override
    public boolean cancel() {
        if (!opened.compareAndSet(true, false)) {
            return false;
        }
        final Future<Object> cancelScalaFuture =
                Patterns.ask(masterTxActor, new CancelRequest(), NetconfTopologyUtils.TIMEOUT);

        LOG.trace("{}: Cancel {} via NETCONF", id);

        try {
            // here must be Await because AsyncWriteTransaction do not return future
            return (boolean) Await.result(cancelScalaFuture, NetconfTopologyUtils.TIMEOUT.duration());
        } catch (final Exception e) {
            return false;
        }
    }

    @Override
    public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        if (!opened.compareAndSet(true, false)) {
            throw new IllegalStateException(id + ": Transaction" + getIdentifier() + " is closed");
        }
        final Future<Object> submitScalaFuture =
                Patterns.ask(masterTxActor, new SubmitRequest(), NetconfTopologyUtils.TIMEOUT);

        LOG.trace("{}: Submit {} via NETCONF", id);

        final SettableFuture<Void> settableFuture = SettableFuture.create();
        submitScalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) throws Throwable {
                if (failure != null) { // ask timeout
                    final Exception exception = NetconfTopologyUtils.createMasterIsDownException(id);
                    settableFuture.setException(exception);
                    return;
                }
                if (success instanceof Throwable) {
                    settableFuture.setException((Throwable) success);
                } else {
                    if (success instanceof SubmitFailedReply) {
                        LOG.error("{}: Transaction was not submitted because already closed.", id);
                    }
                    settableFuture.set(null);
                }
            }
        }, actorSystem.dispatcher());

        return Futures.makeChecked(settableFuture, new Function<Exception, TransactionCommitFailedException>() {
            @Nullable
            @Override
            public TransactionCommitFailedException apply(@Nullable final Exception input) {
                return new TransactionCommitFailedException("Submit of transaction " + getIdentifier() + " failed", input);
            }
        });
    }

    @Override
    public ListenableFuture<RpcResult<TransactionStatus>> commit() {
        LOG.trace("{}: Commit", id);

        final CheckedFuture<Void, TransactionCommitFailedException> submit = submit();
        return Futures.transform(submit, new Function<Void, RpcResult<TransactionStatus>>() {
            @Nullable
            @Override
            public RpcResult<TransactionStatus> apply(@Nullable final Void input) {
                return RpcResultBuilder.success(TransactionStatus.SUBMITED).build();
            }
        });
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier identifier) {
        Preconditions.checkState(opened.get(), "%s: Transaction was closed %s", id, getIdentifier());
        LOG.trace("{}: Delete {} via NETCONF: {}", id, store, identifier);
        masterTxActor.tell(new DeleteRequest(store, identifier), ActorRef.noSender());
    }

    @Override
    public void put(final LogicalDatastoreType store, final YangInstanceIdentifier identifier, final NormalizedNode<?, ?> data) {
        Preconditions.checkState(opened.get(), "%s: Transaction was closed %s", id, getIdentifier());
        final NormalizedNodeMessage msg = new NormalizedNodeMessage(identifier, data);
        LOG.trace("{}: Put {} via NETCONF: {} with payload {}", id, store, identifier, data);
        masterTxActor.tell(new PutRequest(store, msg), ActorRef.noSender());
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier identifier, final NormalizedNode<?, ?> data) {
        Preconditions.checkState(opened.get(), "%s: Transaction was closed %s", id, getIdentifier());
        final NormalizedNodeMessage msg = new NormalizedNodeMessage(identifier, data);
        LOG.trace("{}: Merge {} via NETCONF: {} with payload {}", id, store, identifier, data);
        masterTxActor.tell(new MergeRequest(store, msg), ActorRef.noSender());
    }

    @Override
    public Object getIdentifier() {
        return this;
    }
}
