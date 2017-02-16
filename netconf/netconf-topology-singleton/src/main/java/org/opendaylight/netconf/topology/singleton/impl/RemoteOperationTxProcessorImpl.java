/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.actor.Status;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.RemoteOperationTxProcessor;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitFailedReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitReply;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteOperationTxProcessorImpl implements RemoteOperationTxProcessor, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteOperationTxProcessorImpl.class);

    private final DOMDataBroker dataBroker;
    private final RemoteDeviceId id;
    private DOMDataWriteTransaction writeTx;
    private DOMDataReadOnlyTransaction readTx;

    private ActorRef currentUser = null;

    public RemoteOperationTxProcessorImpl(final DOMDataBroker dataBroker, final RemoteDeviceId id) {
        this.dataBroker = dataBroker;
        this.id = id;
        this.readTx = dataBroker.newReadOnlyTransaction();
    }

    @Override
    public void doOpenTransaction(ActorRef recipient, ActorRef sender) {
        if (currentUser != null) {
            LOG.error("{}: Opening a new transaction for {} failed.", id, recipient);
            recipient.tell(new Status.Failure(
                    new IllegalStateException("Transaction is already opened for another user")), recipient);
            return;
        }

        LOG.debug("{}: Opening a new transaction for {}", id, recipient);
        currentUser = recipient;
        recipient.tell(new Status.Success(null), sender);
    }

    @Override
    public void doDelete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        if (writeTx == null) {
            writeTx = dataBroker.newWriteOnlyTransaction();
        }
        writeTx.delete(store, path);
    }

    @Override
    public void doSubmit(final ActorRef recipient, final ActorRef sender) {
        currentUser = null;
        if (writeTx != null) {
            CheckedFuture<Void, TransactionCommitFailedException> submitFuture = writeTx.submit();
            Futures.addCallback(submitFuture, new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    recipient.tell(new SubmitReply(), sender);
                }

                @Override
                public void onFailure(@Nonnull Throwable throwable) {
                    recipient.tell(throwable, sender);
                }
            });
        } else {
            recipient.tell(new SubmitFailedReply(), sender);
            LOG.warn("{}: Couldn't submit transaction because it was already closed.", id);
        }
    }

    @Override
    public void doCancel(final ActorRef recipient, final ActorRef sender) {
        currentUser = null;
        boolean cancel = false;
        if (writeTx != null) {
            cancel = writeTx.cancel();
        }
        recipient.tell(cancel, sender);

    }

    @Override
    public void doPut(final LogicalDatastoreType store, final NormalizedNodeMessage data) {
        if (writeTx == null) {
            writeTx = dataBroker.newWriteOnlyTransaction();
        }
        writeTx.put(store, data.getIdentifier(), data.getNode());
    }

    @Override
    public void doMerge(final LogicalDatastoreType store, final NormalizedNodeMessage data) {
        if (writeTx == null) {
            writeTx = dataBroker.newWriteOnlyTransaction();
        }
        writeTx.merge(store, data.getIdentifier(), data.getNode());
    }

    @Override
    public void doRead(final LogicalDatastoreType store, final YangInstanceIdentifier path, final ActorRef recipient,
                       final ActorRef sender) {
        final CheckedFuture<Optional<NormalizedNode<?,?>>, ReadFailedException> readFuture =
                readTx.read(store, path);

        Futures.addCallback(readFuture, new FutureCallback<Optional<NormalizedNode<?, ?>>>() {

            @Override
            public void onSuccess(final Optional<NormalizedNode<?, ?>> result) {
                if (!result.isPresent()) {
                    recipient.tell(new EmptyReadResponse(), sender);
                    return;
                }
                recipient.tell(new NormalizedNodeMessage(path, result.get()), sender);
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                recipient.tell(throwable, sender);
            }
        });
    }

    @Override
    public void doExists(final LogicalDatastoreType store, final YangInstanceIdentifier path, final ActorRef recipient,
                         final ActorRef sender) {
        final CheckedFuture<Boolean, ReadFailedException> readFuture =
                readTx.exists(store, path);
        Futures.addCallback(readFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(final Boolean result) {
                if (result == null) {
                    recipient.tell(false, sender);
                } else {
                    recipient.tell(result, sender);
                }
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                recipient.tell(throwable, sender);
            }
        });
    }

    @Override
    public void close() throws Exception {
        currentUser = null;
        if (readTx != null) {
            readTx.close();
        }
    }
}
