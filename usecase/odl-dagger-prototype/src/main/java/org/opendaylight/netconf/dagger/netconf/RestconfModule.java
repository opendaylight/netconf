/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.netconf;

import com.google.errorprone.annotations.DoNotMock;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import javax.servlet.ServletException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.dagger.springboot.config.ConfigLoader;
import org.opendaylight.netconf.databind.DatabindProvider;
import org.opendaylight.odlparent.dagger.ResourceSupport;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.jaxrs.JaxRsEndpoint;
import org.opendaylight.restconf.server.jaxrs.JaxRsEndpointConfiguration;
import org.opendaylight.restconf.server.jaxrs.JaxRsLocationProvider;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.RestconfStream.LocationProvider;
import org.opendaylight.restconf.server.spi.RestconfStream.Registry;

@Module
@DoNotMock
@NonNullByDefault
public interface RestconfModule {

    @Provides
    @Singleton
    static MdsalDatabindProvider mdsalDatabindProvider(final DOMSchemaService schemaService) {
        return new MdsalDatabindProvider(schemaService);
    }

    @Provides
    @Singleton
    static DatabindProvider databindProvider(final MdsalDatabindProvider implementation) {
        return implementation;
    }

    @Provides
    @Singleton
    static RestconfServer restconfServer(final MdsalDatabindProvider databindProvider, final DOMDataBroker dataBroker,
            final DOMRpcService rpcService, final DOMActionService actionService,
            final DOMMountPointService mountPointService, final ResourceSupport resourceSupport) {
        final var mdsalRestconfServer = new MdsalRestconfServer(databindProvider, dataBroker, rpcService, actionService,
            mountPointService);
        resourceSupport.register(mdsalRestconfServer);
        return mdsalRestconfServer;
    }

    @Provides
    @Singleton
    static LocationProvider locationProvider() {
        return new JaxRsLocationProvider();
    }

    @Provides
    @Singleton
    static Registry mdsalRestconfStreamRegistry(final DOMDataBroker dataBroker,
           final DOMNotificationService notificationService, final DOMSchemaService schemaService,
           final LocationProvider locationProvider, final DatabindProvider databindProvider,
           final ClusterSingletonServiceProvider cssProvider) {
        return new MdsalRestconfStreamRegistry(dataBroker, notificationService, schemaService, locationProvider,
            databindProvider, cssProvider);
    }

    @Provides
    @Singleton
    static JaxRsEndpointConfiguration jaxRsEndpointConfiguration(ConfigLoader configLoader) {
        // TODO: Get correct configuration.
        return configLoader.getConfig(JaxRsEndpointConfiguration.class, "", Path.of(""));
    }

    @Provides
    @Singleton
    static JaxRsEndpoint jaxRsEndpoint(final WebServer webServer, final WebContextSecurer webContextSecurer,
            final ServletSupport servletSupport, final CustomFilterAdapterConfiguration filterAdapterConfiguration,
            final RestconfServer server, final Registry streamRegistry, final JaxRsEndpointConfiguration configuration,
            final ResourceSupport resourceSupport) {
        final JaxRsEndpoint jaxRsEndpoint;
        try {
            jaxRsEndpoint = new JaxRsEndpoint(webServer, webContextSecurer, servletSupport,
                filterAdapterConfiguration, server, streamRegistry, configuration);
        } catch (ServletException e) {
            throw new IllegalStateException("Failed to initialize JaxRsEndpoint", e);
        }
        resourceSupport.register(jaxRsEndpoint);
        return jaxRsEndpoint;
    }
}
