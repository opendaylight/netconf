/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.channel.Channel;

/**
 * Abstract base class for {@link TransportChannel}s overlaid on another {@link TransportChannel}.
 */
// FIXME: 9.0.0: move to transport.spi
public abstract class AbstractOverlayTransportChannel extends TransportChannel {
    private final TransportChannel underlay;

    protected AbstractOverlayTransportChannel(final TransportChannel tcp) {
        underlay = requireNonNull(tcp);
    }

    @Override
    public final Channel channel() {
        return underlay.channel();
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("underlay", underlay);
    }
}
