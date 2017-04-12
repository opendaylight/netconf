/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages.transactions;

import akka.actor.ActorRef;
import java.io.Serializable;

public class NewWriteTransactionReply implements Serializable {

    private final ActorRef txActor;

    public NewWriteTransactionReply(ActorRef txActor) {
        this.txActor = txActor;
    }

    public ActorRef getTxActor() {
        return txActor;
    }
}
