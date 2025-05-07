/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import java.net.SocketAddress;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.restconf.server.api.TransportSession.Description;

/**
 * The {@link Description} of a {@link RestconfSession}.
 */
@NonNullByDefault
record HttpDescription(HTTPScheme scheme, SocketAddress remoteAddress) implements Description {
    HttpDescription {
        requireNonNull(scheme);
        requireNonNull(remoteAddress);
    }

    @Override
    public String messageLayer() {
        // FIXME: NETCONF-1419: Include version when have dealt with the HTTP1-vs-HTTP2 mess and allocate
        //                      RestconfSession only after the first request has come it and do this instead:
        //
        //            final var sb = new StringBuilder().append("HTTP/").append(version.majorVersion());
        //            final var minor = version.minorVersion();
        //            if (minor != 0) {
        //                sb.append('.').append(minor);
        //            }
        //            return sb.toString();
        return "HTTP";
    }

    @Override
    public String transportLayer() {
        return switch (scheme) {
            case HTTP -> "TCP";
            // TODO: this could also be DTLS, right?
            // FIXME: we should know the version from TLS negotiation
            case HTTPS -> "TLS";
        };
    }

    @Override
    public String transportPeer() {
        return remoteAddress.toString();
    }

    @Override
    public String toString() {
        return toFriendlyString();
    }
}