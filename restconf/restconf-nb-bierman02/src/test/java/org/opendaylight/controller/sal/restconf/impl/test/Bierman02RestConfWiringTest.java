/*
 * Copyright (c) 2019 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.AbstractModule;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.testutils.TestWebClient;
import org.opendaylight.aaa.web.testutils.WebTestModule;
import org.opendaylight.controller.sal.restconf.impl.test.incubate.InMemoryMdsalModule;
import org.opendaylight.infrautils.inject.guice.testutils.AnnotationsModule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.netconf.sal.restconf.api.RestConfConfig;
import org.opendaylight.netconf.sal.restconf.impl.Bierman02RestConfWiring;

/**
 * Tests if the {@link Bierman02RestConfWiring} works.
 *
 * @author Michael Vorburger.ch
 */
public class Bierman02RestConfWiringTest {

    public static class TestModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(Bierman02RestConfWiring.class).asEagerSingleton();
            bind(RestConfConfig.class).toInstance(() -> 9090);
            bind(CustomFilterAdapterConfiguration.class).toInstance(listener -> { });
        }
    }

    public @Rule LogRule logRule = new LogRule();

    public @Rule GuiceRule guice = new GuiceRule(TestModule.class,
            InMemoryMdsalModule.class, WebTestModule.class, AnnotationsModule.class);

    @Inject WebServer webServer;
    @Inject TestWebClient webClient;

    @Test
    public void testWiring() throws IOException, InterruptedException, URISyntaxException {
        assertThat(webClient.request("get", "/restconf/modules/").statusCode()).isEqualTo(200);
    }
}
