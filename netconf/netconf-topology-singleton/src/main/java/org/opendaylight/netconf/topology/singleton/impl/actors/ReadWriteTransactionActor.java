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
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadActorMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.WriteActorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

public final class ReadWriteTransactionActor extends UntypedActor {

    private static final Logger LOG = LoggerFactory.getLogger(ReadWriteTransactionActor.class);

    private final DOMDataReadWriteTransaction tx;
    private final long idleTimeout;
    private final ReadAdapter readAdapter;
    private final WriteAdapter writeAdapter;

    private ReadWriteTransactionActor(final DOMDataReadWriteTransaction tx, final Duration idleTimeout) {
        this.tx = tx;
        this.idleTimeout = idleTimeout.toSeconds();
        if (this.idleTimeout > 0) {
            context().setReceiveTimeout(idleTimeout);
        }
        readAdapter = new ReadAdapter(tx);
        writeAdapter = new WriteAdapter(tx);
    }

    /**
     * Creates new actor Props.
     *
     * @param tx          delegate device read write transaction
     * @param idleTimeout idle time in seconds, after which transaction is closed automatically
     * @return props
     */
    static Props props(final DOMDataReadWriteTransaction tx, final Duration idleTimeout) {
        return Props.create(ReadWriteTransactionActor.class, () -> new ReadWriteTransactionActor(tx, idleTimeout));
    }

    @Override
    public void onReceive(final Object message) throws Throwable {
        if (message instanceof ReadActorMessage) {
            readAdapter.handle(message, sender(), self());
        } else if (message instanceof WriteActorMessage) {
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
