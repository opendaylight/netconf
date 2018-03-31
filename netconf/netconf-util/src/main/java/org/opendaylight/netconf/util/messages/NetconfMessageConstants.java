/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.messages;

public interface NetconfMessageConstants {

    /**
     * The NETCONF 1.0 old-style message separator. This is framing mechanism
     * is used by default.
     */
    String END_OF_MESSAGE = "]]>]]>";

    // bytes

    int MIN_HEADER_LENGTH = 4;

    // bytes

    int MAX_HEADER_LENGTH = 13;

    String START_OF_CHUNK = "\n#";
    String END_OF_CHUNK = "\n##\n";
}
