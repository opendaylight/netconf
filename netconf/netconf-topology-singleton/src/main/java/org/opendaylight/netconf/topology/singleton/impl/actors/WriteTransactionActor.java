/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.actors;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitFailedReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WriteTransactionActor extends UntypedActor {

    private static final Logger LOG = LoggerFactory.getLogger(WriteTransactionActor.class);
    private final DOMDataWriteTransaction tx;
    private boolean opened = true;

    static Props props(final DOMDataWriteTransaction tx) {
        return Props.create(WriteTransactionActor.class, () -> new WriteTransactionActor(tx));
    }

    private WriteTransactionActor(final DOMDataWriteTransaction tx) {
        this.tx = tx;
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

            boolean cancel = false;
            if (opened) {
                cancel = tx.cancel();
                opened = false;
            }
            sender().tell(cancel, self());
            context().stop(self());

        } else if (message instanceof SubmitRequest) {

            final ActorRef requester = sender();
            final ActorRef self = self();
            final Object id = tx.getIdentifier();
            if (opened) {
                final CheckedFuture<Void, TransactionCommitFailedException> submitFuture = tx.submit();
                opened = false;
                Futures.addCallback(submitFuture, new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(final Void result) {
                        requester.tell(new SubmitReply(), self);
                        self.tell(PoisonPill.getInstance(), ActorRef.noSender());
                    }

                    @Override
                    public void onFailure(@Nonnull final Throwable throwable) {
                        requester.tell(throwable, self);
                        self.tell(PoisonPill.getInstance(), ActorRef.noSender());
                    }
                });
            } else {
                requester.tell(new SubmitFailedReply(), self);
                LOG.warn("{}: Couldn't submit transaction because it was already closed.", id);
            }

        }
    }
}
