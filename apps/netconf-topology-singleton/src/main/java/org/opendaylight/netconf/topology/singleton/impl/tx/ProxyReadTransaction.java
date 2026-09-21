/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.tx;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.ActorRef;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;

/**
 * ProxyReadTransaction uses provided {@link ActorRef} to delegate method calls to master
 * {@link org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActor}.
 */
public class ProxyReadTransaction extends ProxyReadWriteTransaction implements DOMDataTreeReadTransaction {
    public ProxyReadTransaction(final RemoteDeviceId id, final CompletionStage<Object> masterTxActorFuture,
            final Duration askTimeout) {
        super(id, masterTxActorFuture, askTimeout);
    }

    @Override
    public void close() {
        cancel();
    }
}
