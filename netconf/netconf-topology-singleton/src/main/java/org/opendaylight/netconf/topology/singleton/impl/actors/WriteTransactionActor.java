/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
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
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import scala.concurrent.duration.Duration;

/**
 * ReadTransactionActor is an interface to device's {@link DOMDataReadOnlyTransaction} for cluster nodes.
 */
public class WriteTransactionActor extends UntypedActor {

    private final DOMDataWriteTransaction tx;

    /**
     * Creates new actor Props.
     *
     * @param tx delegate device write transaction
     * @param idleTimeout idle time in seconds, after which transaction is closed automatically
     * @return props
     */
    static Props props(final DOMDataWriteTransaction tx, final Duration idleTimeout) {
        return Props.create(WriteTransactionActor.class, () -> new WriteTransactionActor(tx, idleTimeout));
    }

    private WriteTransactionActor(final DOMDataWriteTransaction tx, final Duration idleTimeout) {
        this.tx = tx;
        if (idleTimeout.toSeconds() > 0) {
            context().setReceiveTimeout(idleTimeout);
        }
    }

    @Override
    public void onReceive(final Object message) throws Throwable {
        if (message instanceof MergeRequest) {
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
}
