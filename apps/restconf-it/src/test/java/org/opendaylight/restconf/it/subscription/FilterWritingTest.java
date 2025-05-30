/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.MediaTypes;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class FilterWritingTest extends AbstractNotificationSubscriptionTest {
    private static final String URI = "/restconf/data/ietf-subscribed-notifications:filters";
    private static final String URI_GET = "/restconf/data/ietf-subscribed-notifications:filters/stream-filter=foo";
    private static final String FILTER_XML = """
        <stream-filter xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
         <name>foo</name>
         <stream-subtree-filter>
          <toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/>
         </stream-subtree-filter>
        </stream-filter>""";
    private static final String FILTER_JSON = """
        {
          "stream-filter": {
            "name": "foo",
            "stream-subtree-filter": {
              "toaster:toasterOutOfBread": ""
            }
          }
        }""";
    private static final String EXPECTED_FILTER_XML = """
        <stream-filter xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
          <name>foo</name>
          <stream-subtree-filter>
            <toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/>
          </stream-subtree-filter>
        </stream-filter>""";
    // FIXME should include filter
    private static final String EXPECTED_FILTER_JSON = """
        {
          "ietf-subscribed-notifications:stream-filter":[{
            "name":"foo"
          }]
        }""";

    @BeforeAll
    static void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Disabled("FIXME fails to parse JSON anydata")
    @Test
    void writeJsonSubtreeFilterTest() throws Exception {
        final var postFilterResponse = invokeRequest(HttpMethod.POST, URI, MediaTypes.APPLICATION_YANG_DATA_JSON,
            FILTER_JSON, MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.CREATED, postFilterResponse.status());
    }

    @Test
    void writeXmlSubtreeFilterTest() throws Exception {
        final var postFilterResponse = invokeRequest(HttpMethod.POST, URI, MediaTypes.APPLICATION_YANG_DATA_XML,
            FILTER_XML, MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.CREATED, postFilterResponse.status());
    }

    @Disabled("FIXME fails to parse JSON anydata")
    @Test
    void writeJsonReadJsonSubtreeFilterTest() throws Exception {
        final var postFilterResponse = invokeRequest(HttpMethod.POST, URI, MediaTypes.APPLICATION_YANG_DATA_JSON,
            FILTER_JSON, MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.CREATED, postFilterResponse.status());

        final var getFilterResponse = invokeRequest(HttpMethod.GET, URI_GET, MediaTypes.APPLICATION_YANG_DATA_JSON,
            null, MediaTypes.APPLICATION_YANG_DATA_JSON);
        final var result = getFilterResponse.content().toString(StandardCharsets.UTF_8);
        assertEquals(HttpResponseStatus.OK, getFilterResponse.status());
        JSONAssert.assertEquals(EXPECTED_FILTER_JSON, result, JSONCompareMode.LENIENT);
    }

    @Disabled("FIXME fails to parse JSON anydata")
    @Test
    void writeJsonReadXmlSubtreeFilterTest() throws Exception {
        final var postFilterResponse = invokeRequest(HttpMethod.POST, URI, MediaTypes.APPLICATION_YANG_DATA_JSON,
            FILTER_JSON, MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.CREATED, postFilterResponse.status());

        final var getFilterResponse = invokeRequest(HttpMethod.GET, URI_GET, MediaTypes.APPLICATION_YANG_DATA_XML, null,
            MediaTypes.APPLICATION_YANG_DATA_XML);
        final var result = getFilterResponse.content().toString(StandardCharsets.UTF_8);
        assertEquals(HttpResponseStatus.OK, getFilterResponse.status());
        assertTrue(XMLUnit.compareXML(EXPECTED_FILTER_XML, result).identical());
    }

    @Test
    void writeXmlReadJsonSubtreeFilterTest() throws Exception {
        final var postFilterResponse = invokeRequest(HttpMethod.POST, URI, MediaTypes.APPLICATION_YANG_DATA_XML,
            FILTER_XML, MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.CREATED, postFilterResponse.status());

        final var getFilterResponse = invokeRequest(HttpMethod.GET, URI_GET, MediaTypes.APPLICATION_YANG_DATA_JSON,
            null, MediaTypes.APPLICATION_YANG_DATA_JSON);
        final var result = getFilterResponse.content().toString(StandardCharsets.UTF_8);
        assertEquals(HttpResponseStatus.OK, getFilterResponse.status());
        JSONAssert.assertEquals(EXPECTED_FILTER_JSON, result, JSONCompareMode.LENIENT);
    }

    @Test
    void writeXmlReadXmlSubtreeFilterTest() throws Exception {
        final var postFilterResponse = invokeRequest(HttpMethod.POST, URI, MediaTypes.APPLICATION_YANG_DATA_XML,
            FILTER_XML, MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.CREATED, postFilterResponse.status());

        final var getFilterResponse = invokeRequest(HttpMethod.GET, URI_GET, MediaTypes.APPLICATION_YANG_DATA_XML, null,
            MediaTypes.APPLICATION_YANG_DATA_XML);
        final var result = getFilterResponse.content().toString(StandardCharsets.UTF_8);
        assertEquals(HttpResponseStatus.OK, getFilterResponse.status());
        assertTrue(XMLUnit.compareXML(EXPECTED_FILTER_XML, result).identical());
    }
}
