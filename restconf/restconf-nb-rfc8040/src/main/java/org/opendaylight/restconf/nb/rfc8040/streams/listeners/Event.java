/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import org.opendaylight.restconf.nb.rfc8040.streams.websockets.WebSocketSessionHandler;

/**
 * Represents event of specific {@link EventType} type, holds data and {@link WebSocketSessionHandler} subscriber.
 */
class Event {
    private final EventType type;
    private WebSocketSessionHandler subscriber;
    private String data;

    /**
     * Creates new event specified by {@link EventType} type.
     *
     * @param type Event type.
     */
    Event(final EventType type) {
        this.type = type;
    }

    /**
     * Gets the {@link WebSocketSessionHandler} subscriber.
     *
     * @return Web-socket session handler.
     */
    public WebSocketSessionHandler getSubscriber() {
        return this.subscriber;
    }

    /**
     * Sets subscriber for event.
     *
     * @param subscriber Web-socket session handler.
     */
    public void setSubscriber(final WebSocketSessionHandler subscriber) {
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
     * @param data String representation of event data.
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
