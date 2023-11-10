/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import javax.servlet.http.HttpServlet;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.restconf.server.spi.RestconfStreamRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Auxiliary interface for instantiating JAX-RS streams.
 */
@Component(factory = DefaultRestconfStreamServletFactory.FACTORY_NAME, service = RestconfStreamServletFactory.class)
public final class DefaultRestconfStreamServletFactory implements RestconfStreamServletFactory, AutoCloseable {
    public static final String FACTORY_NAME =
        "org.opendaylight.restconf.nb.rfc8040.streams.RestconfStreamServletFactory";

    private static final String PROP_STREAM_REGISTRY = ".streamRegistry";
    private static final String PROP_NAME_PREFIX = ".namePrefix";
    private static final String PROP_CORE_POOL_SIZE = ".corePoolSize";
    private static final String PROP_USE_WEBSOCKETS = ".useWebsockets";
    private static final String PROP_STREAMS_CONFIGURATION = ".streamsConfiguration";

    private final RestconfStreamRegistry streamRegistry;
    private final ServletSupport servletSupport;

    private final DefaultPingExecutor pingExecutor;
    private final StreamsConfiguration streamsConfiguration;
    private final boolean useWebsockets;

    public DefaultRestconfStreamServletFactory(final ServletSupport servletSupport,
            final RestconfStreamRegistry streamRegistry, final StreamsConfiguration streamsConfiguration,
            final String namePrefix, final int corePoolSize, final boolean useWebsockets) {
        this.servletSupport = requireNonNull(servletSupport);
        this.streamRegistry = requireNonNull(streamRegistry);
        this.streamsConfiguration = requireNonNull(streamsConfiguration);
        this.useWebsockets = useWebsockets;
        pingExecutor = new DefaultPingExecutor(namePrefix, corePoolSize);
    }

    @Activate
    public DefaultRestconfStreamServletFactory(@Reference final ServletSupport servletSupport,
            final Map<String, ?> props) {
        this(servletSupport, (RestconfStreamRegistry) props.get(PROP_STREAM_REGISTRY),
            (StreamsConfiguration) props.get(PROP_STREAMS_CONFIGURATION),
            (String) props.get(PROP_NAME_PREFIX), (int) requireNonNull(props.get(PROP_CORE_POOL_SIZE)),
            (boolean) requireNonNull(props.get(PROP_USE_WEBSOCKETS)));
    }

    @Override
    public HttpServlet newStreamServlet() {
        return useWebsockets ? new WebSocketInitializer(streamRegistry, pingExecutor, streamsConfiguration)
            : servletSupport.createHttpServletBuilder(
                new SSEApplication(streamRegistry, pingExecutor, streamsConfiguration))
            .build();
    }

    @Override
    @Deactivate
    public void close() {
        pingExecutor.close();
    }

    public static Map<String, ?> props(final RestconfStreamRegistry streamRegistry, final boolean useSSE,
            final StreamsConfiguration streamsConfiguration, final String namePrefix, final int corePoolSize) {
        return Map.of(
            PROP_STREAM_REGISTRY, streamRegistry,
            PROP_USE_WEBSOCKETS, !useSSE,
            PROP_STREAMS_CONFIGURATION, streamsConfiguration,
            PROP_NAME_PREFIX, namePrefix,
            PROP_CORE_POOL_SIZE, corePoolSize);
    }
}
