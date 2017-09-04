/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.listeners;

import io.netty.channel.Channel;
import java.util.Set;

/**
 * Base interface for both listeners({@link ListenerAdapter},
 * {@link NotificationListenerAdapter}).
 */
interface BaseListenerInterface extends AutoCloseable {

    /**
     * Return all subscribers of listener.
     *
     * @return set of subscribers
     */
    Set<Channel> getSubscribers();

    /**
     * Checks if exists at least one {@link Channel} subscriber.
     *
     * @return True if exist at least one {@link Channel} subscriber, false
     *         otherwise.
     */
    boolean hasSubscribers();

    /**
     * Get name of stream.
     *
     * @return stream name
     */
    String getStreamName();

    /**
     * Get output type.
     *
     * @return outputType
     */
    String getOutputType();
}
