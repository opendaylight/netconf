/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.OptimisticLockFailedException;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeJsonBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeXmlBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.RestconfDocumentedExceptionMapper;
import org.opendaylight.netconf.sal.rest.impl.XmlNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.PutResult;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

//TODO UNSTABLE TESTS - FIX ME
@Ignore
public class RestPutOperationTest extends JerseyTest {

    private static String xmlData;
    private static String xmlData2;
    private static String xmlData3;

    private static SchemaContext schemaContextYangsIetf;
    private static SchemaContext schemaContextTestModule;

    private BrokerFacade brokerFacade;
    private RestconfImpl restconfImpl;
    private DOMMountPoint mountInstance;

    @BeforeClass
    public static void init() throws IOException, ReactorException {
        schemaContextYangsIetf = TestUtils.loadSchemaContext("/full-versions/yangs");
        schemaContextTestModule = TestUtils.loadSchemaContext("/full-versions/test-module");
        loadData();
    }

    private static void loadData() throws IOException {
        final InputStream xmlStream =
                RestconfImplTest.class.getResourceAsStream("/parts/ietf-interfaces_interfaces.xml");
        xmlData = TestUtils.getDocumentInPrintableForm(TestUtils.loadDocumentFrom(xmlStream));
        final InputStream xmlStream2 =
                RestconfImplTest.class.getResourceAsStream("/full-versions/test-data2/data2.xml");
        xmlData2 = TestUtils.getDocumentInPrintableForm(TestUtils.loadDocumentFrom(xmlStream2));
        final InputStream xmlStream3 =
                RestconfImplTest.class.getResourceAsStream("/full-versions/test-data2/data7.xml");
        xmlData3 = TestUtils.getDocumentInPrintableForm(TestUtils.loadDocumentFrom(xmlStream3));
    }

    @Override
    protected Application configure() {
        /* enable/disable Jersey logs to console */
        // enable(TestProperties.LOG_TRAFFIC);
        // enable(TestProperties.DUMP_ENTITY);
        // enable(TestProperties.RECORD_LOG_LEVEL);
        // set(TestProperties.RECORD_LOG_LEVEL, Level.ALL.intValue());

        mountInstance = mock(DOMMountPoint.class);
        final ControllerContext controllerContext =
                TestRestconfUtils.newControllerContext(schemaContextYangsIetf, mountInstance);
        brokerFacade = mock(BrokerFacade.class);
        restconfImpl = RestconfImpl.newInstance(brokerFacade, controllerContext);

        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig = resourceConfig.registerInstances(restconfImpl,
                new XmlNormalizedNodeBodyReader(controllerContext), new NormalizedNodeXmlBodyWriter(),
                new JsonNormalizedNodeBodyReader(controllerContext), new NormalizedNodeJsonBodyWriter(),
                new RestconfDocumentedExceptionMapper(controllerContext));
        return resourceConfig;
    }

    /**
     * Tests of status codes for "/config/{identifier}".
     */
    @Test
    public void putConfigStatusCodes() throws UnsupportedEncodingException {
        final String uri = "/config/ietf-interfaces:interfaces/interface/eth0";
        mockCommitConfigurationDataPutMethod(true);
        assertEquals(500, put(uri, MediaType.APPLICATION_XML, xmlData));

        mockCommitConfigurationDataPutMethod(false);
        assertEquals(500, put(uri, MediaType.APPLICATION_XML, xmlData));

        assertEquals(400, put(uri, MediaType.APPLICATION_JSON, ""));
    }

    @Test
    public void putConfigStatusCodesEmptyBody() throws UnsupportedEncodingException {
        final String uri = "/config/ietf-interfaces:interfaces/interface/eth0";
        @SuppressWarnings("unused")
        final Response resp = target(uri).request(MediaType.APPLICATION_JSON).put(
                Entity.entity("", MediaType.APPLICATION_JSON));
        assertEquals(400, put(uri, MediaType.APPLICATION_JSON, ""));
    }

    @Test
    public void testRpcResultCommitedToStatusCodesWithMountPoint() throws UnsupportedEncodingException,
            FileNotFoundException, URISyntaxException {
        final PutResult result = mock(PutResult.class);
        when(brokerFacade.commitMountPointDataPut(any(DOMMountPoint.class), any(YangInstanceIdentifier.class),
            any(NormalizedNode.class), null, null)).thenReturn(result);
        doReturn(CommitInfo.emptyFluentFuture()).when(result).getFutureOfPutData();
        when(result.getStatus()).thenReturn(Status.OK);

        when(mountInstance.getSchemaContext()).thenReturn(schemaContextTestModule);

        String uri = "/config/ietf-interfaces:interfaces/interface/0/yang-ext:mount/test-module:cont";
        assertEquals(200, put(uri, MediaType.APPLICATION_XML, xmlData2));

        uri = "/config/ietf-interfaces:interfaces/yang-ext:mount/test-module:cont";
        assertEquals(200, put(uri, MediaType.APPLICATION_XML, xmlData2));
    }

    @Test
    public void putDataMountPointIntoHighestElement() throws UnsupportedEncodingException, URISyntaxException {
        final PutResult result = mock(PutResult.class);
        doReturn(result).when(brokerFacade).commitMountPointDataPut(any(DOMMountPoint.class),
                any(YangInstanceIdentifier.class), any(NormalizedNode.class), null, null);
        doReturn(CommitInfo.emptyFluentFuture()).when(result).getFutureOfPutData();
        when(result.getStatus()).thenReturn(Status.OK);

        when(mountInstance.getSchemaContext()).thenReturn(schemaContextTestModule);

        final String uri = "/config/ietf-interfaces:interfaces/yang-ext:mount";
        assertEquals(200, put(uri, MediaType.APPLICATION_XML, xmlData3));
    }

    @Test
    public void putWithOptimisticLockFailedException() throws UnsupportedEncodingException {

        final String uri = "/config/ietf-interfaces:interfaces/interface/eth0";

        doThrow(OptimisticLockFailedException.class).when(brokerFacade).commitConfigurationDataPut(
                any(SchemaContext.class), any(YangInstanceIdentifier.class), any(NormalizedNode.class), null, null);

        assertEquals(500, put(uri, MediaType.APPLICATION_XML, xmlData));

        doThrow(OptimisticLockFailedException.class).doReturn(mock(PutResult.class)).when(brokerFacade)
                .commitConfigurationDataPut(any(SchemaContext.class), any(YangInstanceIdentifier.class),
                        any(NormalizedNode.class), null, null);

        assertEquals(500, put(uri, MediaType.APPLICATION_XML, xmlData));
    }

    @Test
    public void putWithTransactionCommitFailedException() throws UnsupportedEncodingException {

        final String uri = "/config/ietf-interfaces:interfaces/interface/eth0";

        doThrow(TransactionCommitFailedException.class)
                .when(brokerFacade).commitConfigurationDataPut(
                any(SchemaContext.class), any(YangInstanceIdentifier.class), any(NormalizedNode.class), null, null);

        assertEquals(500, put(uri, MediaType.APPLICATION_XML, xmlData));
    }

    private int put(final String uri, final String mediaType, final String data) throws UnsupportedEncodingException {
        return target(uri).request(mediaType).put(Entity.entity(data, mediaType)).getStatus();
    }

    private void mockCommitConfigurationDataPutMethod(final boolean noErrors) {
        final PutResult putResMock = mock(PutResult.class);
        if (noErrors) {
            doReturn(putResMock).when(brokerFacade).commitConfigurationDataPut(
                    any(SchemaContext.class), any(YangInstanceIdentifier.class), any(NormalizedNode.class), null, null);
        } else {
            doThrow(RestconfDocumentedException.class).when(brokerFacade).commitConfigurationDataPut(
                    any(SchemaContext.class), any(YangInstanceIdentifier.class), any(NormalizedNode.class), null, null);
        }
    }

}
