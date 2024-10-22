/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import com.google.common.base.Stopwatch;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceProvider;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link NettyEndpoint} integrated with OSGi Service Registry.
 */
@Component(factory = OSGiNettyEndpoint.FACTORY_NAME, service = NettyEndpoint.class)
public final class OSGiNettyEndpoint extends NettyEndpoint {
    @NonNullByDefault
    public static final String FACTORY_NAME = "org.opendaylight.restconf.server.NettyEndpoint";

    private static final Logger LOG = LoggerFactory.getLogger(OSGiNettyEndpoint.class);
    private static final String PROP_BOOTSTRAP_FACTORY = ".bootstrapFactory";
    private static final String PROP_CONFIGURATION = ".configuration";

    private final ConcurrentHashMap<WebHostResourceProvider, Registration> providers = new ConcurrentHashMap<>();

    @Activate
    public OSGiNettyEndpoint(@Reference final RestconfServer server, @Reference final PrincipalService principalService,
            @Reference final RestconfStream.Registry streamRegistry, final Map<String, ?> props) {
        super(server, principalService, streamRegistry, (BootstrapFactory) props.get(PROP_BOOTSTRAP_FACTORY),
            (NettyEndpointConfiguration) props.get(PROP_CONFIGURATION));
        LOG.debug("Started endpoint {}", this);
    }

    public static Map<String, ?> props(final BootstrapFactory bootstrapFactory,
            final NettyEndpointConfiguration configuration) {
        return Map.of(PROP_BOOTSTRAP_FACTORY, bootstrapFactory, PROP_CONFIGURATION, configuration);
    }

    @Deactivate
    void deactivate() {
        LOG.debug("Stopping endpoint {}", this);
        final var sw = Stopwatch.createStarted();
        try {
            shutdown().get(1, TimeUnit.MINUTES);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new IllegalStateException("RESTCONF server shutdown failed", e);
        }
        LOG.debug("Stopped endpoint {} in {}", this, sw.stop());
    }

    @NonNullByDefault
    @Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY,
               cardinality = ReferenceCardinality.MULTIPLE)
    void addWebResource(final WebHostResourceProvider provider) {
        final var reg = registerWebResource(provider);
        final var prev = providers.putIfAbsent(provider, reg);
        if (prev != null) {
            LOG.debug("Duplicate registration of {}, terminating {}", provider, reg);
            reg.close();
        }
    }

    @NonNullByDefault
    void removeWebResource(final WebHostResourceProvider provider) {
        @SuppressWarnings("resource")
        final var reg = providers.remove(provider);
        if (reg != null) {
            reg.close();
        } else {
            LOG.debug("Registration for {} not found: probably result of a previous duplicate", provider);
        }
    }
}
