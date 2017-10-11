/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.actors;

import akka.actor.ActorRef;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

class ReadAdapter {

    private final DOMDataReadTransaction tx;

    ReadAdapter(final DOMDataReadTransaction tx) {
        this.tx = tx;
    }

    @SuppressWarnings("checkstyle:IllegalThrows")
    public void handle(final Object message, final ActorRef sender, final ActorRef self) throws Throwable {
        if (message instanceof ReadRequest) {

            final ReadRequest readRequest = (ReadRequest) message;
            final YangInstanceIdentifier path = readRequest.getPath();
            final LogicalDatastoreType store = readRequest.getStore();
            read(path, store, sender, self);

        } else if (message instanceof ExistsRequest) {
            final ExistsRequest readRequest = (ExistsRequest) message;
            final YangInstanceIdentifier path = readRequest.getPath();
            final LogicalDatastoreType store = readRequest.getStore();
            exists(path, store, sender, self);
        }
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
        }, MoreExecutors.directExecutor());
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
        }, MoreExecutors.directExecutor());
    }
}
