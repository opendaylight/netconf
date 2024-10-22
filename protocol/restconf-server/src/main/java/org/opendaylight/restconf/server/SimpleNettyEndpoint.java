/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import com.google.common.annotations.Beta;
import com.google.common.base.Stopwatch;
import java.util.concurrent.ExecutionException;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple implementation of {@link NettyEndpoint}.
 */
@Beta
public final class SimpleNettyEndpoint extends NettyEndpoint implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleNettyEndpoint.class);

    public SimpleNettyEndpoint(final RestconfServer server, final PrincipalService principalService,
            final RestconfStream.Registry streamRegistry, final BootstrapFactory bootstrapFactory,
            final NettyEndpointConfiguration configuration) {
        super(server, principalService, streamRegistry, bootstrapFactory, configuration);
    }

    @Override
    public void close() throws InterruptedException, ExecutionException {
        LOG.debug("Stopping endpoint {}", this);
        final var sw = Stopwatch.createStarted();
        shutdown().get();
        LOG.debug("Stopped endpoint {} in {}", this, sw.stop());
    }
}
