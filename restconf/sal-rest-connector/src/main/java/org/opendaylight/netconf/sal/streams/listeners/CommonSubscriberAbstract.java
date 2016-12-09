/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 *
 */
package org.opendaylight.netconf.sal.streams.listeners;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import io.netty.channel.Channel;
import io.netty.util.internal.ConcurrentSet;
import java.util.Set;
import java.util.concurrent.Executors;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ary
 *
 */
abstract class CommonSubscriberAbstract extends QueryParamsAbstract implements BaseListenerInterface {

    private static final Logger LOG = LoggerFactory.getLogger(CommonSubscriberAbstract.class);

    private final Set<Channel> subscribers = new ConcurrentSet<>();
    private final EventBus eventBus;

    private EventBusChangeRecorder eventBusChangeRecorder;
    private ListenerRegistration registration;

    /**
     *
     */
    protected CommonSubscriberAbstract() {
        this.eventBus = new AsyncEventBus(Executors.newSingleThreadExecutor());
    }

    @Override
    public final boolean hasSubscribers() {
        return !this.subscribers.isEmpty();
    }

    @Override
    public final Set<Channel> getSubscribers(){
        return this.subscribers;
    }

    @Override
    public final void close() throws Exception {
        this.registration.close();
        this.registration = null;

        deleteDataInDS();
        unregister();
    }

    /**
     * Creates event of type {@link EventType#REGISTER}, set {@link Channel}
     * subscriber to the event and post event into event bus.
     *
     * @param subscriber
     *            Channel
     */
    public void addSubscriber(final Channel subscriber) {
        if (!subscriber.isActive()) {
            LOG.debug("Channel is not active between websocket server and subscriber {}" + subscriber.remoteAddress());
        }
        final Event event = new Event(EventType.REGISTER);
        event.setSubscriber(subscriber);
        this.eventBus.post(event);
    }

    /**
     * Creates event of type {@link EventType#DEREGISTER}, sets {@link Channel}
     * subscriber to the event and posts event into event bus.
     *
     * @param subscriber
     */
    public void removeSubscriber(final Channel subscriber) {
        LOG.debug("Subscriber {} is removed.", subscriber.remoteAddress());
        final Event event = new Event(EventType.DEREGISTER);
        event.setSubscriber(subscriber);
        this.eventBus.post(event);
    }

    /**
     * Sets {@link ListenerRegistration} registration.
     *
     * @param registration
     *            DOMDataChangeListener registration
     */
    public void setRegistration(final ListenerRegistration registration) {
        this.registration = registration;
    }

    /**
     * Checks if {@link ListenerRegistration} registration exist.
     *
     * @return True if exist, false otherwise.
     */
    public boolean isListening() {
        return this.registration == null ? false : true;
    }

    /**
     * @param listener
     */
    protected <T extends BaseListenerInterface> void register(final T listener) {
        this.eventBusChangeRecorder = new EventBusChangeRecorder(listener);
        this.eventBus.register(this.eventBusChangeRecorder);
    }

    /**
     * @param event
     */
    protected void post(final Event event) {
        this.eventBus.post(event);
    }

    /**
     * Removes all subscribers and unregisters event bus change recorder form
     * event bus
     */
    protected void unregister() {
        this.subscribers.clear();
        this.eventBus.unregister(this.eventBusChangeRecorder);
    }
}

