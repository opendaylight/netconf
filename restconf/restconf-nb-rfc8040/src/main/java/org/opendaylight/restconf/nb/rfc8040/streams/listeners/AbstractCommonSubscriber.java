/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import com.google.common.base.Preconditions;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.opendaylight.restconf.nb.rfc8040.streams.SessionHandlerInterface;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Features of subscribing part of both notifications.
 */
abstract class AbstractCommonSubscriber extends AbstractQueryParams implements BaseListenerInterface {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCommonSubscriber.class);

    private final Set<SessionHandlerInterface> subscribers = new HashSet<>();
    private volatile ListenerRegistration<?> registration;

    @Override
    public final synchronized boolean hasSubscribers() {
        return !this.subscribers.isEmpty();
    }

    @Override
    public final synchronized Set<SessionHandlerInterface> getSubscribers() {
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
    public synchronized void addSubscriber(final SessionHandlerInterface subscriber) {
        final boolean isConnected = subscriber.isConnected();
        Preconditions.checkState(isConnected);
        LOG.debug("Subscriber {} is added.", subscriber);
        subscribers.add(subscriber);
    }

    @Override
    public synchronized void removeSubscriber(final SessionHandlerInterface subscriber) {
        final boolean isConnected = subscriber.isConnected();
        Preconditions.checkState(isConnected);
        LOG.debug("Subscriber {} is removed", subscriber);
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
     * Post data to subscribed SSE session handlers.
     *
     * @param data Data of incoming notifications.
     */
    synchronized void post(final String data) {
        final Iterator<SessionHandlerInterface> iterator = subscribers.iterator();
        while (iterator.hasNext()) {
            final SessionHandlerInterface subscriber = iterator.next();
            final boolean isConnected = subscriber.isConnected();
            if (isConnected) {
                subscriber.sendDataMessage(data);
                LOG.debug("Data was sent to subscriber {} on connection {}:", this, subscriber);
            } else {
                // removal is probably not necessary, because it will be removed explicitly soon after invocation of
                // onWebSocketClosed(..) in handler; but just to be sure ...
                iterator.remove();
                LOG.debug("Subscriber for {} was removed - web-socket session is not open.", this);
            }
        }
    }
}
