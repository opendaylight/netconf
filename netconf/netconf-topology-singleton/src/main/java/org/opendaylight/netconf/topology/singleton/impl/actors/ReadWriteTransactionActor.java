/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.UntypedActor;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

public class ReadWriteTransactionActor extends UntypedActor {

    private static final Logger LOG = LoggerFactory.getLogger(ReadWriteTransactionActor.class);

    private final DOMDataReadWriteTransaction tx;
    private final long idleTimeout;

    /**
     * Creates new actor Props.
     *
     * @param tx          delegate device read write transaction
     * @param idleTimeout idle time in seconds, after which transaction is closed automatically
     * @return props
     */
    static Props props(final DOMDataReadWriteTransaction tx, final Duration idleTimeout) {
        return Props.create(ReadWriteTransactionActor.class, () -> new ReadWriteTransactionActor(tx, idleTimeout));
    }

    private ReadWriteTransactionActor(final DOMDataReadWriteTransaction tx, final Duration idleTimeout) {
        this.tx = tx;
        this.idleTimeout = idleTimeout.toSeconds();
        if (this.idleTimeout > 0) {
            context().setReceiveTimeout(idleTimeout);
        }
    }

    @Override
    public void onReceive(final Object message) throws Throwable {
        if (message instanceof ReadRequest) {

            final ReadRequest readRequest = (ReadRequest) message;
            final YangInstanceIdentifier path = readRequest.getPath();
            final LogicalDatastoreType store = readRequest.getStore();
            read(path, store, sender(), self());

        } else if (message instanceof ExistsRequest) {
            final ExistsRequest readRequest = (ExistsRequest) message;
            final YangInstanceIdentifier path = readRequest.getPath();
            final LogicalDatastoreType store = readRequest.getStore();
            exists(path, store, sender(), self());

        } else if (message instanceof MergeRequest) {
            final MergeRequest mergeRequest = (MergeRequest) message;
            final NormalizedNodeMessage data = mergeRequest.getNormalizedNodeMessage();
            tx.merge(mergeRequest.getStore(), data.getIdentifier(), data.getNode());
        } else if (message instanceof PutRequest) {
            final PutRequest putRequest = (PutRequest) message;
            final NormalizedNodeMessage data = putRequest.getNormalizedNodeMessage();
            tx.put(putRequest.getStore(), data.getIdentifier(), data.getNode());
        } else if (message instanceof DeleteRequest) {
            final DeleteRequest deleteRequest = (DeleteRequest) message;
            tx.delete(deleteRequest.getStore(), deleteRequest.getPath());
        } else if (message instanceof CancelRequest) {
            cancel();
        } else if (message instanceof SubmitRequest) {
            submit(sender(), self());
        } else if (message instanceof ReceiveTimeout) {
            LOG.warn("Haven't received any message for {} seconds, cancelling transaction and stopping actor",
                    idleTimeout);
            tx.cancel();
            context().stop(self());
        } else {
            unhandled(message);
        }
    }

    private void cancel() {
        final boolean cancelled = tx.cancel();
        sender().tell(cancelled, self());
        context().stop(self());
    }

    private void submit(final ActorRef requester, final ActorRef self) {
        final CheckedFuture<Void, TransactionCommitFailedException> submitFuture = tx.submit();
        context().stop(self);
        Futures.addCallback(submitFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                requester.tell(new SubmitReply(), self);
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                requester.tell(throwable, self);
            }
        });
    }

    private void read(final YangInstanceIdentifier path, final LogicalDatastoreType store, final ActorRef sender,
                      final ActorRef self) {
        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read = tx.read(store, path);
        Futures.addCallback(read, new FutureCallback<Optional<NormalizedNode<?, ?>>>() {

            @Override
            public void onSuccess(final Optional<NormalizedNode<?, ?>> result) {
                if (!result.isPresent()) {
                    sender.tell(new EmptyReadResponse(), self);
                    return;
                }
                sender.tell(new NormalizedNodeMessage(path, result.get()), self);
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                sender.tell(throwable, self);
            }
        });
    }

    private void exists(final YangInstanceIdentifier path, final LogicalDatastoreType store, final ActorRef sender,
                        final ActorRef self) {
        final CheckedFuture<Boolean, ReadFailedException> readFuture = tx.exists(store, path);
        Futures.addCallback(readFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(final Boolean result) {
                if (result == null) {
                    sender.tell(false, self);
                } else {
                    sender.tell(result, self);
                }
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                sender.tell(throwable, self);
            }
        });
    }
}
