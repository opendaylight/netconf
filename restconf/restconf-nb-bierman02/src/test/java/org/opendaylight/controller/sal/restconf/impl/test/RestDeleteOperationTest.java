/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeJsonBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeXmlBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.RestconfDocumentedExceptionMapper;
import org.opendaylight.netconf.sal.rest.impl.XmlNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

public class RestDeleteOperationTest extends JerseyTest {
    private static SchemaContext schemaContext;

    private ControllerContext controllerContext;
    private BrokerFacade brokerFacade;
    private RestconfImpl restconfImpl;

    @BeforeClass
    public static void init() throws FileNotFoundException, ReactorException {
        schemaContext = TestUtils.loadSchemaContext("/test-config-data/yang1");
        final Set<Module> allModules = schemaContext.getModules();
        assertNotNull(allModules);
    }

    @Override
    protected Application configure() {
        /* enable/disable Jersey logs to console */
        // enable(TestProperties.LOG_TRAFFIC);
        // enable(TestProperties.DUMP_ENTITY);
        // enable(TestProperties.RECORD_LOG_LEVEL);
        // set(TestProperties.RECORD_LOG_LEVEL, Level.ALL.intValue());
        controllerContext = TestRestconfUtils.newControllerContext(schemaContext);
        controllerContext.setSchemas(schemaContext);
        brokerFacade = mock(BrokerFacade.class);
        restconfImpl = RestconfImpl.newInstance(brokerFacade, controllerContext);

        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig = resourceConfig.registerInstances(restconfImpl, new NormalizedNodeJsonBodyWriter(),
            new NormalizedNodeXmlBodyWriter(), new XmlNormalizedNodeBodyReader(controllerContext),
            new JsonNormalizedNodeBodyReader(controllerContext),
            new RestconfDocumentedExceptionMapper(controllerContext));
        return resourceConfig;
    }

    @Test
    public void deleteConfigStatusCodes() throws UnsupportedEncodingException {
        final String uri = "/config/test-interface:interfaces";
        doReturn(CommitInfo.emptyFluentFuture()).when(brokerFacade)
            .commitConfigurationDataDelete(any(YangInstanceIdentifier.class));
        Response response = target(uri).request(MediaType.APPLICATION_XML).delete();
        assertEquals(200, response.getStatus());

        doThrow(RestconfDocumentedException.class).when(brokerFacade).commitConfigurationDataDelete(
                any(YangInstanceIdentifier.class));
        response = target(uri).request(MediaType.APPLICATION_XML).delete();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void deleteFailTest() throws Exception {
        final String uri = "/config/test-interface:interfaces";
        doReturn(immediateFailedFluentFuture(new TransactionCommitFailedException("failed test"))).when(brokerFacade)
            .commitConfigurationDataDelete(any(YangInstanceIdentifier.class));
        final Response response = target(uri).request(MediaType.APPLICATION_XML).delete();
        assertEquals(500, response.getStatus());
    }
}
