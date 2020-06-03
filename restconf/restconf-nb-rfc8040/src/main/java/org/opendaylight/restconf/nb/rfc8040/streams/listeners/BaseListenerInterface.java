/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.util.Set;
import org.opendaylight.restconf.nb.rfc8040.streams.SessionHandlerInterface;
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
    Set<SessionHandlerInterface> getSubscribers();

    /**
     * Checks if exists at least one {@link SessionHandlerInterface} subscriber.
     *
     * @return {@code true} if exist at least one {@link SessionHandlerInterface} subscriber, {@code false} otherwise.
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
     * Registers {@link SessionHandlerInterface} subscriber.
     *
     * @param subscriber SSE or WS session handler.
     */
    void addSubscriber(SessionHandlerInterface subscriber);

    /**
     * Removes {@link SessionHandlerInterface} subscriber.
     *
     * @param subscriber SSE or WS session handler.
     */
    void removeSubscriber(SessionHandlerInterface subscriber);

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
