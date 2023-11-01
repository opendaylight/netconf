/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import java.util.Set;

/**
 * Base interface for both listeners({@link ListenerAdapter}, {@link NotificationListenerAdapter}).
 */
public interface BaseListenerInterface extends AutoCloseable {
    /**
     * Return all subscribers of listener.
     *
     * @return Set of all subscribers.
     */
    Set<StreamSessionHandler> getSubscribers();

    /**
     * Checks if exists at least one {@link StreamSessionHandler} subscriber.
     *
     * @return {@code true} if exist at least one {@link StreamSessionHandler} subscriber, {@code false} otherwise.
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
     * Registers {@link StreamSessionHandler} subscriber.
     *
     * @param subscriber SSE or WS session handler.
     */
    void addSubscriber(StreamSessionHandler subscriber);

    /**
     * Removes {@link StreamSessionHandler} subscriber.
     *
     * @param subscriber SSE or WS session handler.
     */
    void removeSubscriber(StreamSessionHandler subscriber);
}
