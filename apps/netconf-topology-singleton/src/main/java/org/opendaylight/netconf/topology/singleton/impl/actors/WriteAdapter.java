/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.actors;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Status.Failure;
import akka.actor.Status.Success;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class WriteAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(WriteAdapter.class);

    private final DOMDataTreeWriteTransaction tx;

    WriteAdapter(final DOMDataTreeWriteTransaction tx) {
        this.tx = tx;
    }

    private void cancel(final ActorContext context, final ActorRef sender, final ActorRef self) {
        final boolean cancelled = tx.cancel();
        sender.tell(cancelled, self);
        context.stop(self);
    }

    private void submit(final ActorRef requester, final ActorRef self, final ActorContext context) {
        final FluentFuture<? extends CommitInfo> submitFuture = tx.commit();
        context.stop(self);
        submitFuture.addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                requester.tell(new Success(null), self);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                requester.tell(new Failure(throwable), self);
            }
        }, MoreExecutors.directExecutor());
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void handle(final Object message, final ActorRef sender, final ActorContext context, final ActorRef self) {
        // we need to catch everything, since an unchecked exception can be thrown from the underlying parse.
        // TODO Maybe we should store it and fail the submit immediately?.
        try {
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
                cancel(context, sender, self);
            } else if (message instanceof SubmitRequest) {
                submit(sender, self, context);
            }

        } catch (final RuntimeException exception) {
            LOG.error("Write command has failed.", exception);
        }
    }
}
