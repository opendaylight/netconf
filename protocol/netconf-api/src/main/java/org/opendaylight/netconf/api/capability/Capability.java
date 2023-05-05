/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.api.messages.HelloMessage;

/**
 * Contains capability URI announced by server hello message and optionally its
 * corresponding yang schema that can be retrieved by get-schema rpc.
 */
@NonNullByDefault
public sealed interface Capability permits SimpleCapability, ParameterizedCapability {
    /**
     * Return this capability's URN.
     *
     * @return An URN string
     */
    String urn();

    /**
     * Return this capability formatted as a URI, suitable for encoding as a {@link HelloMessage} advertisement.
     *
     * @return A URI
     */
    default URI toURI() {
        return URI.create(urn());
    }
}
