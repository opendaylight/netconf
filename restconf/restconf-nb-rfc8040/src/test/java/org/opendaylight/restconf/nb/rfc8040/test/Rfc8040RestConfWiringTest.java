/*
 * Copyright (c) 2019 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.test;

import static com.google.common.truth.Truth.assertThat;
import static org.opendaylight.infrautils.testutils.web.TestWebClient.Method.GET;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Rule;
import org.junit.Test;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.testutils.TestWebClient;
import org.opendaylight.aaa.web.testutils.WebTestModule;
import org.opendaylight.infrautils.inject.guice.testutils.AnnotationsModule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.restconf.nb.rfc8040.RestconfApplication;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040RestConfWiring;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.JSONRestconfServiceRfc8040Impl;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServicesWrapper;
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
        @Override
        protected void configure() {
            bind(Rfc8040RestConfWiring.class).asEagerSingleton();
            bind(RestconfApplication.class).asEagerSingleton();
            bind(JSONRestconfServiceRfc8040Impl.class).asEagerSingleton();
            bind(WebInitializer.class).asEagerSingleton();
            bind(CustomFilterAdapterConfiguration.class).toInstance(listener -> { });
        }

        @Provides
        @Singleton ServicesWrapper getServicesWrapper(Rfc8040RestConfWiring wiring) {
            return wiring.getServicesWrapper();
        }
    }

    public @Rule LogRule logRule = new LogRule();

    public @Rule GuiceRule guice = new GuiceRule(TestModule.class,
            InMemoryMdsalModule.class, WebTestModule.class, AnnotationsModule.class);

    @Inject WebServer webServer;
    @Inject TestWebClient webClient;

    @Inject SchemaContextProvider schemaContextProvider;
    @Inject SchemaContextHandler schemaContextHandler;

    @Test
    public void testWiring() throws IOException {
        schemaContextHandler.onGlobalContextUpdated(schemaContextProvider.getSchemaContext());
        // TODO what URL to test here?  /restconf/modules/ is, apparently, Bierman only..
        // /yang-library-version doesn't work either, so how do we test??  :-(
        assertThat(webClient.request(GET, "/rests/yang-library-version").getStatus()).isEqualTo(200);
        // assertThat(webClient.request(GET, "/rests/data/toaster:toaster?content=nonconfig").getStatus()).isEqualTo(200);
    }
}
