/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.messages;

import static java.util.charset.StandardCharsets.UTF_8;

public final class NetconfMessageConstants {

    private NetconfMessageConstants(){}

    /**
     * The NETCONF 1.0 old-style message separator. This is framing mechanism
     * is used by default.
     */
    public static final byte[] END_OF_MESSAGE = "]]>]]>".getBytes(UTF_8);

    // bytes

    public static final int MIN_HEADER_LENGTH = 4;

    // bytes

    public static final int MAX_HEADER_LENGTH = 13;

    public static final byte[] START_OF_CHUNK = "\n#".getBytes(UTF_8);
    public static final byte[] END_OF_CHUNK = "\n##\n".getBytes(UTF_8);

}
