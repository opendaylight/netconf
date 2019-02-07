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
import java.io.IOException;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.testutils.TestWebClient;
import org.opendaylight.aaa.web.testutils.WebTestModule;
import org.opendaylight.infrautils.inject.guice.testutils.AnnotationsModule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040RestConfWiring;
import org.opendaylight.restconf.nb.rfc8040.test.incubate.InMemoryMdsalModule;

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
            bind(CustomFilterAdapterConfiguration.class).toInstance(listener -> { });
        }
    }

    public @Rule LogRule logRule = new LogRule();

    public @Rule GuiceRule guice = new GuiceRule(TestModule.class,
            InMemoryMdsalModule.class, WebTestModule.class, AnnotationsModule.class);

    @Inject WebServer webServer;
    @Inject TestWebClient webClient;

    @Test
    public void testWiring() throws IOException {
        assertThat(webClient.request(GET, "/restconf/modules/").getStatus()).isEqualTo(200);
    }
}
