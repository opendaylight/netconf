/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.notifications;

import akka.actor.ActorRef;
import java.util.Date;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.dom.api.DOMEvent;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.netconf.topology.singleton.messages.InvokeNotificationListener;

/**
 * Master register this listener as own when slave requests for listener registration. This listener is handled
 * by master for specific slave. The notification is forwarded to the slave after onNotification is invoked by device.
 */
public class RemoteSlaveNotificationListener implements DOMNotificationListener {

    private final ActorRef masterRef;
    private final ActorRef recipient;

    public RemoteSlaveNotificationListener(final ActorRef masterRef, final ActorRef recipient) {
        this.masterRef = masterRef;
        this.recipient = recipient;
    }

    @Override
    public void onNotification(@Nonnull final DOMNotification notification) {
        Date eventTime = null;
        if (notification instanceof DOMEvent) {
            eventTime = ((DOMEvent) notification).getEventTime();
        }
        recipient.tell(new InvokeNotificationListener(notification.getType(), notification.getBody(), eventTime),
                masterRef);
    }
}
