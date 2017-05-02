/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.listeners;

import com.google.common.eventbus.Subscribe;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class EventBusChangeRecorder<T extends BaseListenerInterface> {

    private static final Logger LOG = LoggerFactory.getLogger(EventBusChangeRecorder.class);
    private final T listener;

    /**
     * Event bus change recorder of specific listener of notifications.
     *
     * @param listener
     *             specific listener
     */
    EventBusChangeRecorder(final T listener) {
        this.listener = listener;
    }

    @Subscribe
    public void recordCustomerChange(final Event event) {
        if (event.getType() == EventType.REGISTER) {
            final Channel subscriber = event.getSubscriber();
            if (!this.listener.getSubscribers().contains(subscriber)) {
                this.listener.getSubscribers().add(subscriber);
            }
        } else if (event.getType() == EventType.DEREGISTER) {
            this.listener.getSubscribers().remove(event.getSubscriber());
            Notificator.removeListenerIfNoSubscriberExists(this.listener);
        } else if (event.getType() == EventType.NOTIFY) {
            for (final Channel subscriber : this.listener.getSubscribers()) {
                if (subscriber.isActive()) {
                    LOG.debug("Data are sent to subscriber {}:", subscriber.remoteAddress());
                    subscriber.writeAndFlush(new TextWebSocketFrame(event.getData()));
                } else {
                    LOG.debug("Subscriber {} is removed - channel is not active yet.", subscriber.remoteAddress());
                    this.listener.getSubscribers().remove(subscriber);
                }
            }
        }
    }
}
