/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry.EventStreamFilter;

/**
 * A single subscriber to an {@link RestconfStream}.
 *
 * @param <T> event type
 */
@NonNullByDefault
interface Subscriber<T> {

    EventStreamFilter filter();

    EventFormatter<T> formatter();

    /**
     * Method to send data message to subscriber.
     *
     * @param data Data to send.
     */
    void sendDataMessage(@Nullable String data);

    void endOfStream();
}
