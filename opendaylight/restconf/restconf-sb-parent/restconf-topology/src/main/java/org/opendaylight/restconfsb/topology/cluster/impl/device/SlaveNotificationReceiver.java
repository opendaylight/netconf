/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import akka.actor.Actor;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import com.google.common.base.Preconditions;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.restconfsb.communicator.api.stream.RestconfDeviceStreamListener;
import org.opendaylight.restconfsb.communicator.impl.common.RestconfDeviceNotification;
import org.opendaylight.restconfsb.topology.cluster.impl.messages.NotificationMessage;

/**
 * Actor responsible for handling notification messages. It delegates them to {@link RestconfDeviceStreamListener}
 * provided in constrictor.
 */
class SlaveNotificationReceiver extends UntypedActor {

    private final RestconfDeviceStreamListener delegate;

    private SlaveNotificationReceiver(final RestconfDeviceStreamListener delegate) {
        this.delegate = Preconditions.checkNotNull(delegate);
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof NotificationMessage) {
            final NotificationMessage nm = (NotificationMessage) message;
            final DOMNotification notification = new RestconfDeviceNotification(nm.getContent(), null);
            delegate.onNotification(notification);
        } else {
            unhandled(message);
        }
    }

    /**
     * Create actor {@link Props} used to create new actor.
     *
     * @param delegate received notifications are passed to this listener
     * @return props
     */
    public static Props create(final RestconfDeviceStreamListener delegate) {
        return Props.create(new Creator<Actor>() {
            @Override
            public Actor create() throws Exception {
                return new SlaveNotificationReceiver(delegate);
            }
        });
    }

}
