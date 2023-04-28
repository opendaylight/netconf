/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Known NETCONF framing mechanisms.
 */
@NonNullByDefault
public enum FramingMechanism {
    /**
     * Chunk framing mechanism, as defined in
     * <a href="https://tools.ietf.org/html/rfc6242#section-4.2">RFC6242 Section 4.2</a>.
     */
    CHUNK,
    /**
     * End-of-Message framing mechanism, as defined in
     * <a href="https://tools.ietf.org/html/rfc6242#section-4.3">RFC6242 Section 4.3</a>.
     */
    EOM;

    /**
     * String literal for a start of chunk, i.e. {@code LF HASH} part of {@code chunk} ABNF.
     */
    public static final String CHUNK_START_STR = "\n#";
    /**
     * String representing the end of a chunk, i.e. the {@code LF HASH HASH LF} production {@code end-of-chunks} ABNF.
     */
    public static final String CHUNK_END_STR = "\n##\n";
    /**
     * String representing the End-Of-Message delimiter.
     */
    public static final String EOM_STR = "]]>]]>";
}
