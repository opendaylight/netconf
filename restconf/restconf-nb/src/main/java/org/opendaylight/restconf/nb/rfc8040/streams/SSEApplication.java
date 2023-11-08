/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.restconf.server.spi.RestconfStream;

/**
 * JAX-RS binding for Server-Sent Events.
 */
final class SSEApplication extends Application {
    private final SSEStreamService singleton;

    SSEApplication(final RestconfStream.Registry streamRegistry, final PingExecutor pingExecutor,
            final StreamsConfiguration configuration) {
        singleton = new SSEStreamService(streamRegistry, pingExecutor, configuration);
    }

    @Override
    public Set<Object> getSingletons() {
        return Set.of(singleton);
    }
}
