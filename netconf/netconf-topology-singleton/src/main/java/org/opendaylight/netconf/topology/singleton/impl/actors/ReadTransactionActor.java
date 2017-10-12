/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.actors;

import akka.actor.Props;
import akka.actor.UntypedActor;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadActorMessage;

/**
 * ReadTransactionActor is an interface to device's {@link DOMDataReadOnlyTransaction} for cluster nodes.
 */
public final class ReadTransactionActor extends UntypedActor {

    private final ReadAdapter readAdapter;

    private ReadTransactionActor(final DOMDataReadOnlyTransaction tx) {
        readAdapter = new ReadAdapter(tx);
    }

    /**
     * Creates new actor Props.
     *
     * @param tx delegate device read transaction
     * @return props
     */
    static Props props(final DOMDataReadOnlyTransaction tx) {
        return Props.create(ReadTransactionActor.class, () -> new ReadTransactionActor(tx));
    }

    @Override
    public void onReceive(final Object message) throws Throwable {
        if (message instanceof ReadActorMessage) {
            readAdapter.handle(message, sender(), self());
        } else {
            unhandled(message);
        }
    }

}
