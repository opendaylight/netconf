/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.HashSet;
import java.util.Set;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Features of subscribing part of both notifications.
 */
abstract class AbstractCommonSubscriber extends AbstractQueryParams implements BaseListenerInterface {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCommonSubscriber.class);

    private final Set<Channel> subscribers = new HashSet<>();
    private volatile ListenerRegistration<?> registration;

    @Override
    public final synchronized boolean hasSubscribers() {
        return !this.subscribers.isEmpty();
    }

    @Override
    public final synchronized Set<Channel> getSubscribers() {
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
    public synchronized void addSubscriber(final Channel subscriber) {
        if (!subscriber.isActive()) {
            LOG.debug("Channel is not active between websocket server and subscriber {}", subscriber.remoteAddress());
        }
        subscribers.add(subscriber);
    }

    @Override
    public synchronized void removeSubscriber(final Channel subscriber) {
        LOG.debug("Subscriber {} is removed.", subscriber.remoteAddress());
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
     * Post data to subscribed channels.
     *
     * @param data Data of incoming notifications.
     */
    synchronized void post(final String data) {
        for (final Channel subscriber : subscribers) {
            if (subscriber.isActive()) {
                LOG.debug("Data are sent to subscriber {}:", subscriber.remoteAddress());
                subscriber.writeAndFlush(new TextWebSocketFrame(data));
            } else {
                LOG.debug("Subscriber {} is removed - channel is not active yet.", subscriber.remoteAddress());
                subscribers.remove(subscriber);
            }
        }
    }
}
