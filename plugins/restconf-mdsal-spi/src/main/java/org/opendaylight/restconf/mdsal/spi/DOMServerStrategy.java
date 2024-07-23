/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMService;
import org.opendaylight.restconf.server.spi.ServerStrategy;

/**
 * A {@link DOMService} exposing a {@link ServerStrategy}.
 */
@NonNullByDefault
public record DOMServerStrategy(ServerStrategy serverStrategy)
        implements DOMService<DOMServerStrategy, DOMServerStrategy.Extension> {
    public static final class Extension implements DOMService.Extension<DOMServerStrategy, Extension> {
        private Extension() {
            // Never instantiated
        }
    }

    public DOMServerStrategy {
        requireNonNull(serverStrategy);
    }
}
