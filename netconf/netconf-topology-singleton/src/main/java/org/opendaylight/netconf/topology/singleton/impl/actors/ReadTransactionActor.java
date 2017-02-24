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
import akka.actor.UntypedActor;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class ReadTransactionActor extends UntypedActor {

    private final DOMDataReadOnlyTransaction tx;

    static Props props(final DOMDataReadOnlyTransaction tx) {
        return Props.create(ReadTransactionActor.class, () -> new ReadTransactionActor(tx));
    }

    private ReadTransactionActor(final DOMDataReadOnlyTransaction tx) {
        this.tx = tx;
    }

    @Override
    public void onReceive(final Object message) throws Throwable {
        if (message instanceof ReadRequest) {

            final ReadRequest readRequest = (ReadRequest) message;
            final YangInstanceIdentifier path = readRequest.getPath();
            final LogicalDatastoreType store = readRequest.getStore();
            final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read = tx.read(store, path);
            final ActorRef sender = sender();
            final ActorRef self = self();
            Futures.addCallback(read, new FutureCallback<Optional<NormalizedNode<?, ?>>>() {

                @Override
                public void onSuccess(final Optional<NormalizedNode<?, ?>> result) {
                    if (!result.isPresent()) {
                        sender.tell(new EmptyReadResponse(), self());
                        return;
                    }
                    sender.tell(new NormalizedNodeMessage(path, result.get()), self);
                }

                @Override
                public void onFailure(@Nonnull final Throwable throwable) {
                    sender.tell(throwable, self);
                }
            });

        } else if (message instanceof ExistsRequest) {
            final ExistsRequest readRequest = (ExistsRequest) message;
            final YangInstanceIdentifier path = readRequest.getPath();
            final LogicalDatastoreType store = readRequest.getStore();
            final CheckedFuture<Boolean, ReadFailedException> read = tx.exists(store, path);
            final ActorRef sender = sender();
            final ActorRef self = self();
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
}
