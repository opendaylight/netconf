/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import java.time.Duration;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.Patterns;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.spi.PingPongMergingDOMDataBroker;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.tx.ProxyReadTransaction;
import org.opendaylight.netconf.topology.singleton.impl.tx.ProxyReadWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadWriteTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionRequest;

public class ProxyDOMDataBroker implements PingPongMergingDOMDataBroker {
    private final Duration askTimeout;
    private final RemoteDeviceId id;
    private final ActorRef masterNode;

    /**
     * Constructor for {@code ProxyDOMDataBroker}.
     *
     * @param id          id
     * @param masterNode  {@link org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor} ref
     * @param askTimeout  ask timeout
     */
    public ProxyDOMDataBroker(final RemoteDeviceId id, final ActorRef masterNode, final Duration askTimeout) {
        this.id = id;
        this.masterNode = masterNode;
        this.askTimeout = askTimeout;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public DOMDataTreeReadTransaction newReadOnlyTransaction() {
        return new ProxyReadTransaction(id, Patterns.ask(masterNode, new NewReadTransactionRequest(), askTimeout),
            askTimeout);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public DOMDataTreeReadWriteTransaction newReadWriteTransaction() {
        return new ProxyReadWriteTransaction(id,
            Patterns.ask(masterNode, new NewReadWriteTransactionRequest(), askTimeout), askTimeout);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public DOMDataTreeWriteTransaction newWriteOnlyTransaction() {
        return new ProxyReadWriteTransaction(id, Patterns.ask(masterNode, new NewWriteTransactionRequest(), askTimeout),
            askTimeout);
    }

    @Override
    public DOMTransactionChain createTransactionChain() {
        throw new UnsupportedOperationException(id + ": Transaction chains not supported for netconf mount point");
    }
}
