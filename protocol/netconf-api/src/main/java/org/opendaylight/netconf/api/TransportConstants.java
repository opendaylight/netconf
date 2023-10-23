/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Various constants related to NETCONF transport layer.
 */
@NonNullByDefault
public final class TransportConstants {
    /**
     * The name of the SSH subsystem used to carry NETCONF sessions, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6242#section-7">RFC6242</a>.
     */
    public static final String SSH_SUBSYSTEM = "netconf";

    /**
     * The default TCP port to use for NETCONF over SSH, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6242#section-7">RFC6242</a>.
     */
    public static final int SSH_TCP_PORT = 830;

    private TransportConstants() {
        // Hidden on purpose
    }
}
