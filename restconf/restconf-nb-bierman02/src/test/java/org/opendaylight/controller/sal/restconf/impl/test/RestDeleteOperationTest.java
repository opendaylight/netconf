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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
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

    private static ControllerContext controllerContext;
    private static BrokerFacade brokerFacade;
    private static RestconfImpl restconfImpl;

    @BeforeClass
    public static void init() throws FileNotFoundException, ReactorException {
        final SchemaContext schemaContext = TestUtils.loadSchemaContext("/test-config-data/yang1");
        final Set<Module> allModules = schemaContext.getModules();
        assertNotNull(allModules);

        controllerContext = ControllerContext.getInstance();
        controllerContext.setSchemas(schemaContext);
        brokerFacade = mock(BrokerFacade.class);
        restconfImpl = RestconfImpl.getInstance();
        restconfImpl.setBroker(brokerFacade);
        restconfImpl.setControllerContext(controllerContext);
    }

    @Override
    protected Application configure() {
        /* enable/disable Jersey logs to console */
        // enable(TestProperties.LOG_TRAFFIC);
        // enable(TestProperties.DUMP_ENTITY);
        // enable(TestProperties.RECORD_LOG_LEVEL);
        // set(TestProperties.RECORD_LOG_LEVEL, Level.ALL.intValue());
        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig = resourceConfig.registerInstances(restconfImpl, new NormalizedNodeJsonBodyWriter(),
            new NormalizedNodeXmlBodyWriter(), new XmlNormalizedNodeBodyReader(), new JsonNormalizedNodeBodyReader());
        resourceConfig.registerClasses(RestconfDocumentedExceptionMapper.class);
        return resourceConfig;
    }

    @Test
    public void deleteConfigStatusCodes() throws UnsupportedEncodingException {
        final String uri = "/config/test-interface:interfaces";
        when(brokerFacade.commitConfigurationDataDelete(any(YangInstanceIdentifier.class))).thenReturn(
                Futures.immediateCheckedFuture(null));
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
        final CheckedFuture<Void, TransactionCommitFailedException> future =
                Futures.immediateFailedCheckedFuture(new TransactionCommitFailedException("failed test"));
        when(brokerFacade.commitConfigurationDataDelete(any(YangInstanceIdentifier.class))).thenReturn(future);
        final Response response = target(uri).request(MediaType.APPLICATION_XML).delete();
        assertEquals(500, response.getStatus());
    }
}
