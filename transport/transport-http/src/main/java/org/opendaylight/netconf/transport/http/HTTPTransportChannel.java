/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpScheme;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.AbstractOverlayTransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannel;

@NonNullByDefault
public final class HTTPTransportChannel extends AbstractOverlayTransportChannel {
    private final HttpScheme scheme;

    HTTPTransportChannel(final TransportChannel underlay, final HttpScheme scheme) {
        super(underlay);
        this.scheme = requireNonNull(scheme);
    }

    /**
     * Return the {@link HttpScheme} underlying this channel.
     *
     * @return the {@link HttpScheme} underlying this channel
     */
    public HttpScheme scheme() {
        return scheme;
    }
}
