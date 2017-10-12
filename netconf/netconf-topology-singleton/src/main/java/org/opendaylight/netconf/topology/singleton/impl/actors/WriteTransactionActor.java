/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.actors;

import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.UntypedActor;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.transactions.WriteActorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

/**
 * WriteTransactionActor is an interface to device's {@link DOMDataReadOnlyTransaction} for cluster nodes.
 */
public final class WriteTransactionActor extends UntypedActor {

    private static final Logger LOG = LoggerFactory.getLogger(WriteTransactionActor.class);

    private final DOMDataWriteTransaction tx;
    private final long idleTimeout;
    private final WriteAdapter writeAdapter;

    private WriteTransactionActor(final DOMDataWriteTransaction tx, final Duration idleTimeout) {
        this.tx = tx;
        this.idleTimeout = idleTimeout.toSeconds();
        if (this.idleTimeout > 0) {
            context().setReceiveTimeout(idleTimeout);
        }
        writeAdapter = new WriteAdapter(tx);
    }

    /**
     * Creates new actor Props.
     *
     * @param tx          delegate device write transaction
     * @param idleTimeout idle time in seconds, after which transaction is closed automatically
     * @return props
     */
    static Props props(final DOMDataWriteTransaction tx, final Duration idleTimeout) {
        return Props.create(WriteTransactionActor.class, () -> new WriteTransactionActor(tx, idleTimeout));
    }

    @Override
    public void onReceive(final Object message) throws Throwable {
        if (message instanceof WriteActorMessage) {
            writeAdapter.handle(message, sender(), context(), self());
        } else if (message instanceof ReceiveTimeout) {
            LOG.warn("Haven't received any message for {} seconds, cancelling transaction and stopping actor",
                    idleTimeout);
            tx.cancel();
            context().stop(self());
        } else {
            unhandled(message);
        }
    }


}
