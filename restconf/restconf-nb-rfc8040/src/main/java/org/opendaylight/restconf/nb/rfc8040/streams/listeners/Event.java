/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import io.netty.channel.Channel;

/**
 * Represents event of specific {@link EventType} type, holds data and
 * {@link Channel} subscriber.
 */
class Event {
    private final EventType type;
    private Channel subscriber;
    private String data;

    /**
     * Creates new event specified by {@link EventType} type.
     *
     * @param type
     *            EventType
     */
    Event(final EventType type) {
        this.type = type;
    }

    /**
     * Gets the {@link Channel} subscriber.
     *
     * @return Channel
     */
    public Channel getSubscriber() {
        return this.subscriber;
    }

    /**
     * Sets subscriber for event.
     *
     * @param subscriber
     *            Channel
     */
    public void setSubscriber(final Channel subscriber) {
        this.subscriber = subscriber;
    }

    /**
     * Gets event String.
     *
     * @return String representation of event data.
     */
    public String getData() {
        return this.data;
    }

    /**
     * Sets event data.
     *
     * @param data
     *            String.
     */
    public void setData(final String data) {
        this.data = data;
    }

    /**
     * Gets event type.
     *
     * @return The type of the event.
     */
    public EventType getType() {
        return this.type;
    }
}
