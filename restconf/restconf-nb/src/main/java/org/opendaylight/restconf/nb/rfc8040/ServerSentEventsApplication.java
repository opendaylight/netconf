/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfDataStreamServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.streams.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;

/**
 * JAX-RS binding for Server-Sent Events.
 */
final class ServerSentEventsApplication extends Application {
    private final RestconfDataStreamServiceImpl singleton;

    ServerSentEventsApplication(final ScheduledThreadPool scheduledThreadPool, final ListenersBroker listenersBroker,
            final StreamsConfiguration configuration) {
        singleton = new RestconfDataStreamServiceImpl(scheduledThreadPool, listenersBroker, configuration);
    }

    @Override
    public Set<Object> getSingletons() {
        return Set.of(singleton);
    }
}
