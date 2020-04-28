/*
 * Copyright (c) 2019 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.test;

<<<<<<< HEAD   (bbfd77 Factor out common operations schema/data construction)
import static org.junit.Assert.assertEquals;
=======
//import static com.google.common.truth.Truth.assertThat;
//import static org.opendaylight.infrautils.testutils.web.TestWebClient.Method.GET;
>>>>>>> CHANGE (2808b3 [RFC8040] Implement RESTCONF Extensibility (discovery))

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
<<<<<<< HEAD   (bbfd77 Factor out common operations schema/data construction)
import java.io.IOException;
import java.net.URISyntaxException;
=======
//import java.io.IOException;
>>>>>>> CHANGE (2808b3 [RFC8040] Implement RESTCONF Extensibility (discovery))
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Rule;
//import org.junit.Test;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.testutils.TestWebClient;
import org.opendaylight.aaa.web.testutils.WebTestModule;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.util.ScheduledThreadPoolWrapper;
import org.opendaylight.infrautils.inject.guice.testutils.AnnotationsModule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.restconf.nb.rfc8040.RestconfApplication;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040RestConfWiring;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.JSONRestconfServiceRfc8040Impl;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServicesWrapper;
import org.opendaylight.restconf.nb.rfc8040.streams.websockets.WebSocketConfiguration;
import org.opendaylight.restconf.nb.rfc8040.test.incubate.InMemoryMdsalModule;
import org.opendaylight.restconf.nb.rfc8040.web.WebInitializer;
import org.opendaylight.yangtools.yang.model.api.SchemaContextProvider;

/**
 * Tests if the {@link Rfc8040RestConfWiring} works.
 *
 * @author Michael Vorburger.ch
 */
public class Rfc8040RestConfWiringTest {

    public static class TestModule extends AbstractModule {

        private static final WebSocketConfiguration SAMPLE_WEB_SOCKET_CONFIGURATION
                = new WebSocketConfiguration(8192, 30000, 5000);

        @Override
        protected void configure() {
            bind(Rfc8040RestConfWiring.class).asEagerSingleton();
            bind(RestconfApplication.class).asEagerSingleton();
            bind(JSONRestconfServiceRfc8040Impl.class).asEagerSingleton();
            bind(WebInitializer.class).asEagerSingleton();
            bind(CustomFilterAdapterConfiguration.class).toInstance(listener -> { });
            bind(WebSocketConfiguration.class).toInstance(SAMPLE_WEB_SOCKET_CONFIGURATION);
        }

        @Provides
        @Singleton ServicesWrapper getServicesWrapper(final Rfc8040RestConfWiring wiring) {
            return wiring.getServicesWrapper();
        }

        @Provides
        @Singleton ScheduledThreadPool getScheduledThreadPool() {
            return new ScheduledThreadPoolWrapper(8, new ThreadFactoryBuilder().build());
        }
    }

    public @Rule GuiceRule guice = new GuiceRule(TestModule.class,
            InMemoryMdsalModule.class, WebTestModule.class, AnnotationsModule.class);

    @Inject WebServer webServer;
    @Inject TestWebClient webClient;

    @Inject SchemaContextProvider schemaContextProvider;
    @Inject SchemaContextHandler schemaContextHandler;

<<<<<<< HEAD   (bbfd77 Factor out common operations schema/data construction)
    @Test
    public void testWiring() throws IOException, InterruptedException, URISyntaxException {
        schemaContextHandler.onGlobalContextUpdated(schemaContextProvider.getSchemaContext());
        assertEquals(200, webClient.request("GET", "/rests/yang-library-version").statusCode());
    }
=======
//    @Test
//    public void testWiring() throws IOException {
//        schemaContextHandler.onGlobalContextUpdated(schemaContextProvider.getSchemaContext());
//        assertThat(webClient.request(GET, "/rests/yang-library-version").getStatus()).isEqualTo(200);
//    }
>>>>>>> CHANGE (2808b3 [RFC8040] Implement RESTCONF Extensibility (discovery))
}
