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
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * A transport session on which a {@link ServerRequest} is occurring. This typically the TCP or TLS connection
 * underlying an HTTP request.
 */
@Beta
@NonNullByDefault
public interface TransportSession {
    /**
     * Register resource to be closed on session end. Implementation should guarantee all registration are closed
     * when session ends.
     *
     * @param <T> resource type
     * @param resource resource to be registered
     * @return An {@link ObjectRegistration} or the resource, unregistered via {@link ObjectRegistration#close()}, or
     *         {@code null} on registration failure.
     */
    <T extends Registration> @Nullable ObjectRegistration<T> registerResource(T resource);
}
