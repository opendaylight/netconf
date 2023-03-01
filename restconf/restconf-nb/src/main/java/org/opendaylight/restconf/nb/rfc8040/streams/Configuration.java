/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;

/**
 * Restconf configuration holder and verifier.
 */
public class Configuration {

    private static final int MAX_FRAGMENT_LENGTH = 65535;

    private final int maximumFragmentLength;
    private final int idleTimeout;
    private final int heartbeatInterval;
    private final boolean useSSE;

    /**
     * Creation of the restconf configuration holder with verification of input parameters.
     *
     * @param maximumFragmentLength Maximum web-socket fragment length in number of Unicode code units (characters)
     *                              (exceeded message length leads to fragmentation of messages).
     * @param idleTimeout           Maximum idle time of web-socket session before the session is closed (milliseconds).
     * @param heartbeatInterval     Interval in milliseconds between sending of ping control frames.
     * @param useSSE                when is true use SSE else use WS
     */
    public Configuration(final int maximumFragmentLength, final int idleTimeout, final int heartbeatInterval,
            final boolean useSSE) {
        checkArgument(idleTimeout > 0, "Idle timeout must be specified by positive value.");
        checkArgument(maximumFragmentLength >= 0 && maximumFragmentLength < MAX_FRAGMENT_LENGTH,
            "Maximum fragment length must be disabled (0) or specified by positive value less than 64 KB.");
        checkArgument(heartbeatInterval >= 0,
            "Heartbeat ping interval must be disabled (0) or specified by positive value.");
        // we need at least one heartbeat before we time out the socket
        checkArgument(idleTimeout > heartbeatInterval, "Idle timeout must be greater than heartbeat ping interval.");

        this.maximumFragmentLength = maximumFragmentLength;
        this.idleTimeout = idleTimeout;
        this.heartbeatInterval = heartbeatInterval;
        this.useSSE = useSSE;
    }

    public int getMaximumFragmentLength() {
        return maximumFragmentLength;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * Getter for useSSE.
     *
     * @return true in situation when we use SSE. False in situation when we use WS
     */
    public boolean isUseSSE() {
        return useSSE;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("maximumFragmentLength", maximumFragmentLength)
                .add("idleTimeout", idleTimeout)
                .add("heartbeatInterval", heartbeatInterval)
                .add("useSSE", useSSE)
                .toString();
    }
}
