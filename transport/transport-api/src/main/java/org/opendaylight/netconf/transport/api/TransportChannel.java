/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import org.eclipse.jdt.annotation.NonNull;

/**
 * A transport-level session. This concept is bound to a {@link Channel} for now, so as to enforce type-safety. It acts
 * as a meeting point between a logical NETCONF session and the underlying transport.
 */
public record TransportChannel(@NonNull Channel channel) {
    public TransportChannel {
        requireNonNull(channel);
    }
}
