/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.restconfsb.communicator.api.stream.RestconfDeviceStreamListener;
import org.opendaylight.restconfsb.topology.cluster.impl.messages.NotificationMessage;

/**
 * ClusterNotificationDistributor is created on master peer. It implements {@link RestconfDeviceStreamListener},
 * so it can be registered and notified about notifications received from device. When notification is received,
 * it sends {@link NotificationMessage} to all {@link ActorRef}s, which has been registered via
 * {@link ClusterNotificationDistributor#addSubscriber(ActorRef)}.
 */
class ClusterNotificationDistributor implements RestconfDeviceStreamListener, AutoCloseable {

    private final List<ActorRef> peers = Collections.synchronizedList(new ArrayList<ActorRef>());
    private final ActorContext context;

    public ClusterNotificationDistributor(final ActorContext context) {
        this.context = context;
    }

    @Override
    public void onNotification(final DOMNotification notification) {
        for (final ActorRef peer : peers) {
            peer.tell(new NotificationMessage(notification), ActorRef.noSender());
        }
    }

    /**
     * Add subscriber. {@link NotificationMessage}s are sent to the subscribers.
     *
     * @param subscriber subscriber ref
     */
    public void addSubscriber(final ActorRef subscriber) {
        peers.add(subscriber);
        context.watch(subscriber);
    }

    /**
     * Remove subscriber.
     *
     * @param actor actor to be removed
     */
    public void removeSubscriber(final ActorRef actor) {
        peers.remove(actor);
    }

    @Override
    public void close() throws Exception {
        for (final ActorRef peer : peers) {
            peer.tell(PoisonPill.getInstance(), ActorRef.noSender());
        }
    }
}
