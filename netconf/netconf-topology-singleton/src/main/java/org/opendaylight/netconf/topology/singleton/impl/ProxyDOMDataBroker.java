/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.base.Verify;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.tx.ProxyReadTransaction;
import org.opendaylight.netconf.topology.singleton.impl.tx.ProxyReadWriteTransaction;
import org.opendaylight.netconf.topology.singleton.impl.tx.ProxyWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadWriteTransactionReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadWriteTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionRequest;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import scala.concurrent.Await;
import scala.concurrent.Future;

public class ProxyDOMDataBroker implements DOMDataBroker {

    private final Timeout askTimeout;
    private final RemoteDeviceId id;
    private final ActorRef masterNode;
    private final ActorSystem actorSystem;

    /**
     * Constructor for {@code ProxyDOMDataBroker}.
     *
     * @param actorSystem system
     * @param id          id
     * @param masterNode  {@link org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor} ref
     * @param askTimeout  ask timeout
     */
    public ProxyDOMDataBroker(final ActorSystem actorSystem, final RemoteDeviceId id,
                              final ActorRef masterNode, final Timeout askTimeout) {
        this.id = id;
        this.masterNode = masterNode;
        this.actorSystem = actorSystem;
        this.askTimeout = askTimeout;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public DOMDataReadOnlyTransaction newReadOnlyTransaction() {
        final Future<Object> txActorFuture = Patterns.ask(masterNode, new NewReadTransactionRequest(), askTimeout);
        final Object msg;
        try {
            msg = Await.result(txActorFuture, askTimeout.duration());
        } catch (Exception e) {
            throw new IllegalStateException("Can't create ProxyReadTransaction", e);
        }

        if (msg instanceof Exception) {
            throw new IllegalStateException("Can't create ProxyReadTransaction", (Exception) msg);
        }

        Verify.verify(msg instanceof NewReadTransactionReply);
        final NewReadTransactionReply reply = (NewReadTransactionReply) msg;
        return new ProxyReadTransaction(reply.getTxActor(), id, actorSystem, askTimeout);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public DOMDataReadWriteTransaction newReadWriteTransaction() {
        final Future<Object> txActorFuture = Patterns.ask(masterNode, new NewReadWriteTransactionRequest(), askTimeout);
        final Object msg;
        try {
            msg = Await.result(txActorFuture, askTimeout.duration());
        } catch (Exception e) {
            throw new IllegalStateException("Can't create ProxyReadWriteTransaction", e);
        }

        if (msg instanceof Exception) {
            throw new IllegalStateException("Can't create ProxyReadWriteTransaction", (Exception) msg);
        }

        Verify.verify(msg instanceof NewReadWriteTransactionReply);
        final NewReadWriteTransactionReply reply = (NewReadWriteTransactionReply) msg;
        return new ProxyReadWriteTransaction(reply.getTxActor(), id, actorSystem, askTimeout);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public DOMDataWriteTransaction newWriteOnlyTransaction() {
        final Future<Object> txActorFuture = Patterns.ask(masterNode, new NewWriteTransactionRequest(), askTimeout);
        final Object msg;
        try {
            msg = Await.result(txActorFuture, askTimeout.duration());
        } catch (Exception e) {
            throw new IllegalStateException("Can't create ProxyWriteTransaction", e);
        }

        if (msg instanceof Exception) {
            throw new IllegalStateException("Can't create ProxyWriteTransaction", (Exception) msg);
        }

        Verify.verify(msg instanceof NewWriteTransactionReply);
        final NewWriteTransactionReply reply = (NewWriteTransactionReply) msg;
        return new ProxyWriteTransaction(reply.getTxActor(), id, actorSystem, askTimeout);
    }

    @Override
    public ListenerRegistration<DOMDataChangeListener> registerDataChangeListener(
            final LogicalDatastoreType store, final YangInstanceIdentifier path, final DOMDataChangeListener listener,
            final DataChangeScope triggeringScope) {
        throw new UnsupportedOperationException(id + ": Data change listeners not supported for netconf mount point");
    }

    @Override
    public DOMTransactionChain createTransactionChain(final TransactionChainListener listener) {
        throw new UnsupportedOperationException(id + ": Transaction chains not supported for netconf mount point");
    }

    @Nonnull
    @Override
    public Map<Class<? extends DOMDataBrokerExtension>, DOMDataBrokerExtension> getSupportedExtensions() {
        return Collections.emptyMap();
    }
}
