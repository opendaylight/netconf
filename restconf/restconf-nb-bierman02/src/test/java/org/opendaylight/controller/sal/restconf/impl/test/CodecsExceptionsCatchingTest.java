/*
 * Copyright (c) 2014, 2015 Brocade Communication Systems, Inc., Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.FileNotFoundException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeJsonBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeXmlBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.XmlNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

public class CodecsExceptionsCatchingTest extends JerseyTest {

    private RestconfImpl restConf;
    private ControllerContext controllerContext;

    @Before
    public void init() throws FileNotFoundException, ReactorException {
        restConf = RestconfImpl.newInstance(mock(BrokerFacade.class), controllerContext);
        final SchemaContext schemaContext = TestUtils.loadSchemaContext("/decoding-exception/yang");
        controllerContext = TestRestconfUtils.newControllerContext(schemaContext);
    }

    @Override
    protected Application configure() {
        /* enable/disable Jersey logs to console */
        // enable(TestProperties.LOG_TRAFFIC);
        // enable(TestProperties.DUMP_ENTITY);
        // enable(TestProperties.RECORD_LOG_LEVEL);
        // set(TestProperties.RECORD_LOG_LEVEL, Level.ALL.intValue());
        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig = resourceConfig.registerInstances(restConf, new NormalizedNodeJsonBodyWriter(),
            new NormalizedNodeXmlBodyWriter(), new XmlNormalizedNodeBodyReader(controllerContext),
            new JsonNormalizedNodeBodyReader(controllerContext));
        return resourceConfig;
    }

    @Test
    @Ignore // TODO RestconfDocumentedExceptionMapper needs be fixed before
    public void stringToNumberConversionError() {
        final Response response = target("/config/number:cont").request(MediaType.APPLICATION_XML).put(
                Entity.entity("<cont xmlns=\"number\"><lf>3f</lf></cont>", MediaType.APPLICATION_XML));
        final String exceptionMessage = response.readEntity(String.class);
        assertTrue(exceptionMessage.contains("invalid-value"));
    }
}
