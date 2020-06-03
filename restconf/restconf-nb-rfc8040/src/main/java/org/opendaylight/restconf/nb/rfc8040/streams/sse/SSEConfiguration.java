/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.sse;

import com.google.common.base.Preconditions;

public class SSEConfiguration {

    //TODO: is this need? in well formated json with CR/LF is send every line as new packet
    private final int maximumFragmentLength;
    private final int heartbeatInterval;

    private static final int MAX_FRAGMENT_LENGTH = 65535;

    /**
     * Creation of the server-sent events configuration holder with verification of input parameters.
     *
     * @param maximumFragmentLength Maximum SSE fragment length in number of Unicode code units (characters)
     *                              (exceeded message length leads to fragmentation of messages).
     * @param heartbeatInterval     Interval in milliseconds between sending of ping control frames.
     */
    public SSEConfiguration(final int maximumFragmentLength, final int heartbeatInterval) {
        Preconditions.checkArgument(maximumFragmentLength >= 0 && maximumFragmentLength < MAX_FRAGMENT_LENGTH,
                "Maximum fragment length must be disabled (0) or specified by positive value "
                        + "less than 64 KB.");
        Preconditions.checkArgument(heartbeatInterval >= 0, "Heartbeat ping interval must be "
                + "disabled (0) or specified by positive value.");
        this.maximumFragmentLength = maximumFragmentLength;
        this.heartbeatInterval = heartbeatInterval;
    }

    public int getMaximumFragmentLength() {
        return maximumFragmentLength;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }
}
