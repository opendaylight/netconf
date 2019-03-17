/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import io.netty.channel.Channel;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Features of subscribing part of both notifications.
 */
abstract class AbstractCommonSubscriber extends AbstractQueryParams implements BaseListenerInterface {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCommonSubscriber.class);

    private final Set<Channel> subscribers = ConcurrentHashMap.newKeySet();
    private final EventBus eventBus;

    private EventBusChangeRecorder eventBusChangeRecorder;

    private volatile ListenerRegistration<?> registration;

    /**
     * Creating {@link EventBus}.
     */
    AbstractCommonSubscriber() {
        this.eventBus = new AsyncEventBus(Executors.newSingleThreadExecutor());
    }

    @Override
    public final boolean hasSubscribers() {
        return !this.subscribers.isEmpty();
    }

    @Override
    public final Set<Channel> getSubscribers() {
        return this.subscribers;
    }

    @Override
    public final void close() throws Exception {
        if (this.registration != null) {
            this.registration.close();
            this.registration = null;
        }
        deleteDataInDS();
        unregister();
    }

    @Override
    public void addSubscriber(final Channel subscriber) {
        if (!subscriber.isActive()) {
            LOG.debug("Channel is not active between websocket server and subscriber {}", subscriber.remoteAddress());
        }
        final Event event = new Event(EventType.REGISTER);
        event.setSubscriber(subscriber);
        this.eventBus.post(event);
    }

    @Override
    public void removeSubscriber(final Channel subscriber) {
        LOG.debug("Subscriber {} is removed.", subscriber.remoteAddress());
        final Event event = new Event(EventType.DEREGISTER);
        event.setSubscriber(subscriber);
        this.eventBus.post(event);
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
     * Creating and registering {@link EventBusChangeRecorder} of specific
     * listener on {@link EventBus}.
     *
     * @param listener Specific listener of notifications.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    <T extends BaseListenerInterface> void register(final T listener) {
        this.eventBusChangeRecorder = new EventBusChangeRecorder(listener);
        this.eventBus.register(this.eventBusChangeRecorder);
    }

    /**
     * Post event to event bus.
     *
     * @param event Data of incoming notifications.
     */
    protected void post(final Event event) {
        this.eventBus.post(event);
    }

    /**
     * Removes all subscribers and unregisters event bus change recorder form event bus.
     */
    private void unregister() {
        this.subscribers.clear();
        this.eventBus.unregister(this.eventBusChangeRecorder);
    }
}
