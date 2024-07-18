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
import java.util.Set;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.core.Application;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Auxiliary interface for instantiating JAX-RS streams.
 *
 * @deprecated This componet exists only to support SSE/Websocket delivery. It will be removed when support for
 *             WebSockets is removed.
 */
@Component(factory = DefaultRestconfStreamServletFactory.FACTORY_NAME, service = RestconfStreamServletFactory.class)
@Deprecated(since = "7.0.0", forRemoval = true)
public final class DefaultRestconfStreamServletFactory implements RestconfStreamServletFactory, AutoCloseable {
    public static final String FACTORY_NAME =
        "org.opendaylight.restconf.nb.rfc8040.streams.RestconfStreamServletFactory";

    private static final String PROP_NAME_PREFIX = ".namePrefix";
    private static final String PROP_CORE_POOL_SIZE = ".corePoolSize";
    private static final String PROP_STREAMS_CONFIGURATION = ".streamsConfiguration";
    private static final String PROP_RESTCONF = ".restconf";

    private final @NonNull String restconf;
    private final RestconfStream.Registry streamRegistry;
    private final ServletSupport servletSupport;

    private final DefaultPingExecutor pingExecutor;
    private final StreamsConfiguration streamsConfiguration;

    public DefaultRestconfStreamServletFactory(final ServletSupport servletSupport, final String restconf,
            final RestconfStream.Registry streamRegistry, final StreamsConfiguration streamsConfiguration,
            final String namePrefix, final int corePoolSize) {
        this.servletSupport = requireNonNull(servletSupport);
        this.restconf = requireNonNull(restconf);
        if (restconf.endsWith("/")) {
            throw new IllegalArgumentException("{+restconf} value ends with /");
        }
        this.streamRegistry = requireNonNull(streamRegistry);
        this.streamsConfiguration = requireNonNull(streamsConfiguration);
        pingExecutor = new DefaultPingExecutor(namePrefix, corePoolSize);
    }

    @Activate
    public DefaultRestconfStreamServletFactory(@Reference final ServletSupport servletSupport,
            @Reference final RestconfStream.Registry streamRegistry, final Map<String, ?> props) {
        this(servletSupport, (String) props.get(PROP_RESTCONF), streamRegistry,
            (StreamsConfiguration) props.get(PROP_STREAMS_CONFIGURATION),
            (String) props.get(PROP_NAME_PREFIX), (int) requireNonNull(props.get(PROP_CORE_POOL_SIZE)));
    }

    @Override
    public String restconf() {
        return restconf;
    }

    @Override
    public HttpServlet newStreamServlet() {
        return servletSupport.createHttpServletBuilder(
            new Application() {
                @Override
                public Set<Object> getSingletons() {
                    return Set.of(new SSEStreamService(streamRegistry, pingExecutor, streamsConfiguration));
                }
            }).build();
    }

    @Override
    @Deactivate
    public void close() {
        pingExecutor.close();
    }

    public static Map<String, ?> props(final String restconf, final StreamsConfiguration streamsConfiguration,
            final String namePrefix, final int corePoolSize) {
        return Map.of(
            PROP_RESTCONF, restconf,
            PROP_STREAMS_CONFIGURATION, streamsConfiguration,
            PROP_NAME_PREFIX, namePrefix,
            PROP_CORE_POOL_SIZE, corePoolSize);
    }
}
