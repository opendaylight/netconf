/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * RESTCONF configuration holder and verifier.
 *
 * @param maximumFragmentLength Maximum web-socket fragment length in number of Unicode code units (characters)
 *                              (exceeded message length leads to fragmentation of messages).
 * @param idleTimeout           Maximum idle time of web-socket session before the session is closed (milliseconds).
 * @param heartbeatInterval     Interval in milliseconds between sending of ping control frames.
 * @param useSSE                when is {@code true} use SSE else use WS
 */
public record StreamsConfiguration(
        int maximumFragmentLength,
        int idleTimeout,
        int heartbeatInterval,
        boolean useSSE,
        String basePath) {
    // FIXME: can this be 64KiB exactly? if so, maximumFragmentLength should become a Uint16 and validation should be
    //        pushed out to users
    public static final int MAXIMUM_FRAGMENT_LENGTH_LIMIT = 65534;

    public StreamsConfiguration {
        checkArgument(maximumFragmentLength >= 0 && maximumFragmentLength <= MAXIMUM_FRAGMENT_LENGTH_LIMIT,
            "Maximum fragment length must be disabled (0) or specified by positive value less than 64KiB");
        checkArgument(idleTimeout > 0, "Idle timeout must be specified by positive value.");
        checkArgument(heartbeatInterval >= 0,
            "Heartbeat ping interval must be disabled (0) or specified by positive value.");
        // we need at least one heartbeat before we time out the socket
        checkArgument(idleTimeout > heartbeatInterval, "Idle timeout must be greater than heartbeat ping interval.");
    }
}