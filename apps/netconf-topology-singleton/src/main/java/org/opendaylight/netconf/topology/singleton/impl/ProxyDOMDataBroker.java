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
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import org.opendaylight.mdsal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.mdsal.dom.spi.PingPongMergingDOMDataBroker;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.tx.ProxyReadTransaction;
import org.opendaylight.netconf.topology.singleton.impl.tx.ProxyReadWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadWriteTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionRequest;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

public class ProxyDOMDataBroker implements PingPongMergingDOMDataBroker {

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
    public DOMDataTreeReadTransaction newReadOnlyTransaction() {
        final Future<Object> txActorFuture = Patterns.ask(masterNode, new NewReadTransactionRequest(), askTimeout);
        return new ProxyReadTransaction(id, txActorFuture, executionContext, askTimeout);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public DOMDataTreeReadWriteTransaction newReadWriteTransaction() {
        final Future<Object> txActorFuture = Patterns.ask(masterNode, new NewReadWriteTransactionRequest(), askTimeout);
        return new ProxyReadWriteTransaction(id, txActorFuture, executionContext, askTimeout);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public DOMDataTreeWriteTransaction newWriteOnlyTransaction() {
        final Future<Object> txActorFuture = Patterns.ask(masterNode, new NewWriteTransactionRequest(), askTimeout);
        return new ProxyReadWriteTransaction(id, txActorFuture, executionContext, askTimeout);
    }

    @Override
    public DOMTransactionChain createTransactionChain(final DOMTransactionChainListener listener) {
        throw new UnsupportedOperationException(id + ": Transaction chains not supported for netconf mount point");
    }

    @Override
    public ClassToInstanceMap<DOMDataBrokerExtension> getExtensions() {
        return ImmutableClassToInstanceMap.of();
    }
}
