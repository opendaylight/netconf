/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Transport-level channel bootstrap.
 */
@NonNullByDefault
@FunctionalInterface
public interface TransportChannelListener {
    /**
     * Invoked when a {@link TransportChannel} is established. Implementations of this method are expected to attach
     * to validate the channel and connect it to the messages layer.
     *
     * @param channel Established channel
     */
    void onTransportChannelEstablished(TransportChannel channel);
}
