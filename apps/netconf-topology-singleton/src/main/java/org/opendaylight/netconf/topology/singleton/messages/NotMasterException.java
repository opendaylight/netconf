/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages;

import akka.actor.ActorRef;
import java.io.Serial;

/**
 * Exception reply indicating the recipient is not the master.
 *
 * @author Thomas Pantelis
 */
public class NotMasterException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    public NotMasterException(final ActorRef recipient) {
        super("Actor " + recipient + " is not the current master");
    }
}
