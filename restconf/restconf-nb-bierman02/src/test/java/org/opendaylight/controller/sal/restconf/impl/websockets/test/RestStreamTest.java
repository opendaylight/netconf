/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.websockets.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.controller.sal.restconf.impl.test.TestUtils;
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeJsonBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeXmlBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.RestconfDocumentedExceptionMapper;
import org.opendaylight.netconf.sal.rest.impl.XmlNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class RestStreamTest extends JerseyTest {

    private static EffectiveModelContext schemaContextYangsIetf;

    private BrokerFacade brokerFacade;
    private RestconfImpl restconfImpl;

    @BeforeClass
    public static void init() throws FileNotFoundException, ReactorException {
        schemaContextYangsIetf = TestUtils.loadSchemaContext("/full-versions/yangs");
    }

    @Override
    protected Application configure() {
        /* enable/disable Jersey logs to console */
        // enable(TestProperties.LOG_TRAFFIC);
        // enable(TestProperties.DUMP_ENTITY);
        // enable(TestProperties.RECORD_LOG_LEVEL);
        // set(TestProperties.RECORD_LOG_LEVEL, Level.ALL.intValue());

        final ControllerContext controllerContext = TestRestconfUtils.newControllerContext(schemaContextYangsIetf);
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
    @Ignore // Sporadic failures where jersey does not correctly pass post data to XmlNormalizedNodeBodyReader.readFrom
    public void testCallRpcCallGet() throws UnsupportedEncodingException, InterruptedException {
        createAndSubscribe(null);
    }

    @Test
    @Ignore // Sporadic failures where jersey does not correctly pass post data to XmlNormalizedNodeBodyReader.readFrom
    public void testCallRpcCallGetLeaves() throws UnsupportedEncodingException, InterruptedException {
        createAndSubscribe("odl-leaf-nodes-only", "true");
    }

    private void createAndSubscribe(final String queryParamName, final Object... values)
                                                throws UnsupportedEncodingException, InterruptedException {
        String uri = "/operations/sal-remote:create-data-change-event-subscription";
        String rpcInput = getRpcInput();
        final Response responseWithStreamName = post(uri, MediaType.APPLICATION_XML, rpcInput);
        final Document xmlResponse = responseWithStreamName.readEntity(Document.class);
        assertNotNull(xmlResponse);
        final Element outputElement = xmlResponse.getDocumentElement();
        assertEquals("output",outputElement.getLocalName());

        final Node streamNameElement = outputElement.getFirstChild();
        assertEquals("stream-name",streamNameElement.getLocalName());
        assertEquals("data-change-event-subscription/ietf-interfaces:interfaces/ietf-interfaces:interface/eth0/"
                + "datastore=CONFIGURATION/scope=BASE",streamNameElement.getTextContent());

        uri = "/streams/stream/data-change-event-subscription/ietf-interfaces:interfaces/ietf-interfaces:interface/"
                + "eth0/datastore=CONFIGURATION/scope=BASE";
        final Response responseWithRedirectionUri = get(uri, MediaType.APPLICATION_XML, null);
        final URI websocketServerUri = responseWithRedirectionUri.getLocation();
        assertNotNull(websocketServerUri);
        assertTrue(websocketServerUri.toString().matches(".*ws://localhost:[\\d]+/data-change-event-subscription/"
                + "ietf-interfaces:interfaces/ietf-interfaces:interface/eth0.*"));
    }

    private Response post(final String uri, final String mediaType, final String data) {
        return target(uri).request(mediaType).post(Entity.entity(data, mediaType));
    }

    private Response get(final String uri, final String mediaType, final String queryParam, final Object... values) {
        if (queryParam != null) {
            return target(uri).queryParam(queryParam, values).request(mediaType).get();
        } else {
            return target(uri).request(mediaType).get();
        }
    }

    private static String getRpcInput() {
        final StringBuilder sb = new StringBuilder();
        sb.append("<input xmlns=\"urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote\">");
        sb.append("<path xmlns:int=\"urn:ietf:params:xml:ns:yang:ietf-interfaces\">/"
                + "int:interfaces/int:interface[int:name='eth0']</path>");
        sb.append("</input>");
        return sb.toString();
    }

}
