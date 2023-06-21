/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import akka.actor.ActorRef;
import java.io.Serial;
import java.io.Serializable;

/**
 * After master is connected, slaves send the message to master and master triggers registering slave mount point
 * with reply 'RegisterMountPoint' which includes needed parameters.
 */
public class AskForMasterMountPoint implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ActorRef slaveActorRef;

    public AskForMasterMountPoint(ActorRef slaveActorRef) {
        this.slaveActorRef = slaveActorRef;
    }

    public ActorRef getSlaveActorRef() {
        return slaveActorRef;
    }

    @Override
    public String toString() {
        return "AskForMasterMountPoint [slaveActorRef=" + slaveActorRef + "]";
    }
}
