/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * A transport session on which a {@link ServerRequest} is occurring. This typically the TCP or TLS connection
 * underlying an HTTP request.
 */
@Beta
@NonNullByDefault
public interface TransportSession {
    /**
     * Register resource to be closed on session close.
     *
     * @param registration resource to be registered
     */
    void registerResource(Registration registration);

    /**
     * Closes this session and close all registered resources registered by {@code registerResource}.
     * If the session is already closed then invoking this method has no effect.
     */
    void close();
}
