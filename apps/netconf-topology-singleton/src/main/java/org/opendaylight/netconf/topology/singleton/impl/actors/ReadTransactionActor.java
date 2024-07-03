/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.actors;

import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.UntypedAbstractActor;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadActorMessage;

/**
 * ReadTransactionActor is an interface to device's {@link DOMDataTreeReadTransaction} for cluster nodes.
 */
public final class ReadTransactionActor extends UntypedAbstractActor {

    private final ReadAdapter readAdapter;

    private ReadTransactionActor(final DOMDataTreeReadTransaction tx) {
        readAdapter = new ReadAdapter(tx);
    }

    /**
     * Creates new actor Props.
     *
     * @param tx delegate device read transaction
     * @return props
     */
    static Props props(final DOMDataTreeReadTransaction tx) {
        return Props.create(ReadTransactionActor.class, () -> new ReadTransactionActor(tx));
    }

    @Override
    public void onReceive(final Object message) {
        if (message instanceof ReadActorMessage) {
            readAdapter.handle(message, sender(), self());
        } else {
            unhandled(message);
        }
    }

}
