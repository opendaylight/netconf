/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.tx.ProxyReadTransaction;
import org.opendaylight.netconf.topology.singleton.impl.tx.ProxyReadWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadWriteTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionRequest;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

public class ProxyDOMDataBroker implements DOMDataBroker {

    private final Timeout askTimeout;
    private final RemoteDeviceId id;
    private final ActorRef masterNode;
    private final ExecutionContext executionContext;

    /**
     * Constructor for {@code ProxyDOMDataBroker}.
     *
     * @param id          id
     * @param masterNode  {@link org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor} ref
     * @param executionContext ExecutionContext
     * @param askTimeout  ask timeout
     */
    public ProxyDOMDataBroker(final RemoteDeviceId id, final ActorRef masterNode,
            final ExecutionContext executionContext, final Timeout askTimeout) {
        this.id = id;
        this.masterNode = masterNode;
        this.executionContext = executionContext;
        this.askTimeout = askTimeout;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public DOMDataReadOnlyTransaction newReadOnlyTransaction() {
        final Future<Object> txActorFuture = Patterns.ask(masterNode, new NewReadTransactionRequest(), askTimeout);
        return new ProxyReadTransaction(id, txActorFuture, executionContext, askTimeout);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public DOMDataReadWriteTransaction newReadWriteTransaction() {
        final Future<Object> txActorFuture = Patterns.ask(masterNode, new NewReadWriteTransactionRequest(), askTimeout);
        return new ProxyReadWriteTransaction(id, txActorFuture, executionContext, askTimeout);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public DOMDataWriteTransaction newWriteOnlyTransaction() {
        final Future<Object> txActorFuture = Patterns.ask(masterNode, new NewWriteTransactionRequest(), askTimeout);
        return new ProxyReadWriteTransaction(id, txActorFuture, executionContext, askTimeout);
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
