/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.AbstractOverlayTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

public abstract sealed class HTTPTransportStack extends AbstractOverlayTransportStack<HTTPTransportChannel>
        permits HTTPClient, HTTPServer {
    static final int MAX_HTTP_CONTENT_LENGTH = 16 * 1024;

    private final @NonNull HTTPScheme scheme;

    HTTPTransportStack(final TransportChannelListener<? super HTTPTransportChannel> listener, final HTTPScheme scheme) {
        super(listener);
        this.scheme = requireNonNull(scheme);
    }

    final @NonNull HTTPScheme scheme() {
        return scheme;
    }
}
