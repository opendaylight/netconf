/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.util.Set;
import org.opendaylight.restconf.nb.rfc8040.streams.websockets.WebSocketSessionHandler;
import org.opendaylight.yangtools.concepts.ListenerRegistration;

/**
 * Base interface for both listeners({@link ListenerAdapter}, {@link NotificationListenerAdapter}).
 */
public interface BaseListenerInterface extends AutoCloseable {

    /**
     * Return all subscribers of listener.
     *
     * @return Set of all subscribers.
     */
    Set<WebSocketSessionHandler> getSubscribers();

    /**
     * Checks if exists at least one {@link WebSocketSessionHandler} subscriber.
     *
     * @return {@code true} if exist at least one {@link WebSocketSessionHandler} subscriber, {@code false} otherwise.
     */
    boolean hasSubscribers();

    /**
     * Get name of stream.
     *
     * @return Stream name.
     */
    String getStreamName();

    /**
     * Get output type.
     *
     * @return Output type (JSON or XML).
     */
    String getOutputType();

    /**
     * Registers {@link WebSocketSessionHandler} subscriber.
     *
     * @param subscriber Web-socket session handler.
     */
    void addSubscriber(WebSocketSessionHandler subscriber);

    /**
     * Removes {@link WebSocketSessionHandler} subscriber.
     *
     * @param subscriber Web-socket session handler.
     */
    void removeSubscriber(WebSocketSessionHandler subscriber);

    /**
     * Sets {@link ListenerRegistration} registration.
     *
     * @param registration DOMDataChangeListener registration.
     */
    void setRegistration(ListenerRegistration<?> registration);

    /**
     * Checks if {@link ListenerRegistration} registration exists.
     *
     * @return {@code true} if exists, {@code false} otherwise.
     */
    boolean isListening();
}
