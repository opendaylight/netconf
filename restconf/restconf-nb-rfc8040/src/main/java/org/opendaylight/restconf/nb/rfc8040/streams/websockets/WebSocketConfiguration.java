/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.websockets;

import com.google.common.base.Preconditions;

/**
 * Web-socket configuration holder and verifier.
 */
public class WebSocketConfiguration {

    private static final int MAX_FRAGMENT_LENGTH = 65535;

    private final int maximumFragmentLength;
    private final int idleTimeout;
    private final int heartbeatInterval;

    /**
     * Creation of the web-socket configuration holder with verification of input parameters.
     *
     * @param maximumFragmentLength Maximum web-socket fragment length in number of Unicode code units (characters)
     *                              (exceeded message length leads to fragmentation of messages).
     * @param idleTimeout           Maximum idle time of web-socket session before the session is closed (milliseconds).
     * @param heartbeatInterval     Interval in milliseconds between sending of ping control frames.
     */
    public WebSocketConfiguration(int maximumFragmentLength, int idleTimeout, int heartbeatInterval) {
        Preconditions.checkArgument(idleTimeout > 0, "Idle timeout must be specified by positive value.");
        Preconditions.checkArgument(maximumFragmentLength >= 0 && maximumFragmentLength < MAX_FRAGMENT_LENGTH,
                "Maximum fragment length must be disabled (0) or specified by positive value "
                        + "less than 64 KB.");
        Preconditions.checkArgument(heartbeatInterval >= 0, "Heartbeat ping interval must be "
                + "disabled (0) or specified by positive value.");

        this.maximumFragmentLength = maximumFragmentLength;
        this.idleTimeout = idleTimeout;
        this.heartbeatInterval = heartbeatInterval;
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
}