/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import com.google.common.base.Preconditions;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.opendaylight.restconf.nb.rfc8040.streams.websockets.WebSocketSessionHandler;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Features of subscribing part of both notifications.
 */
abstract class AbstractCommonSubscriber extends AbstractQueryParams implements BaseListenerInterface {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCommonSubscriber.class);

    private final Set<WebSocketSessionHandler> subscribers = new HashSet<>();
    private volatile ListenerRegistration<?> registration;

    @Override
    public final synchronized boolean hasSubscribers() {
        return !this.subscribers.isEmpty();
    }

    @Override
    public final synchronized Set<WebSocketSessionHandler> getSubscribers() {
        return new HashSet<>(this.subscribers);
    }

    @Override
    public final synchronized void close() throws Exception {
        if (this.registration != null) {
            this.registration.close();
            this.registration = null;
        }
        deleteDataInDS();
        this.subscribers.clear();
    }

    @Override
    public synchronized void addSubscriber(final WebSocketSessionHandler subscriber) {
        final Optional<InetSocketAddress> remoteEndpointAddress = subscriber.getRemoteEndpointAddress();
        Preconditions.checkState(remoteEndpointAddress.isPresent());
        LOG.debug("Subscriber {} is added.", remoteEndpointAddress.get());
        subscribers.add(subscriber);
    }

    @Override
    public synchronized void removeSubscriber(final WebSocketSessionHandler subscriber) {
        final Optional<InetSocketAddress> remoteEndpointAddress = subscriber.getRemoteEndpointAddress();
        Preconditions.checkState(remoteEndpointAddress.isPresent());
        LOG.debug("Subscriber {} is removed.", remoteEndpointAddress.get());
        subscribers.remove(subscriber);
        if (!hasSubscribers()) {
            ListenersBroker.getInstance().removeAndCloseListener(this);
        }
    }

    @Override
    public void setRegistration(final ListenerRegistration<?> registration) {
        this.registration = registration;
    }

    @Override
    public boolean isListening() {
        return this.registration != null;
    }

    /**
     * Post data to subscribed web-socket session handlers.
     *
     * @param data Data of incoming notifications.
     */
    synchronized void post(final String data) {
        for (final WebSocketSessionHandler subscriber : subscribers) {
            final Optional<InetSocketAddress> remoteEndpointAddress = subscriber.getRemoteEndpointAddress();
            if (remoteEndpointAddress.isPresent()) {
                subscriber.sendDataMessage(data);
                LOG.debug("Data was sent to subscriber {} on address {}:", this, remoteEndpointAddress.get());
            } else {
                // removal is probably not necessary, because it will be removed explicitly soon after invocation of
                // onWebSocketClosed(..) in handler; but just to be sure ...
                subscribers.remove(subscriber);
                LOG.debug("Subscriber for {} was removed - web-socket session is not open.", this);
            }
        }
    }
}