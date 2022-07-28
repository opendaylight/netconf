/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util.messages;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public final class NetconfMessageConstants {
    /**
     * The NETCONF 1.0 old-style message separator. This is framing mechanism is used by default.
     */
    public static final String END_OF_MESSAGE = "]]>]]>";

    // bytes
    @Deprecated(since = "4.0.0", forRemoval = true)
    public static final int MIN_HEADER_LENGTH = 4;

    // bytes
    @Deprecated(since = "4.0.0", forRemoval = true)
    public static final int MAX_HEADER_LENGTH = 13;

    public static final String START_OF_CHUNK = "\n#";
    public static final String END_OF_CHUNK = "\n##\n";

    private NetconfMessageConstants() {
        // Hidden on purpose
    }
}
