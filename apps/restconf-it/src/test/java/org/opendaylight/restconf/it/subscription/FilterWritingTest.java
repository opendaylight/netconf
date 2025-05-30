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
    @BeforeAll
    static void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Disabled("FIXME fails to find toasterOutOfBread under stream-subtree-filter while POSTing JSON filter")
    @Test
    void writeJsonReadJsonSubtreeFilterTest() throws Exception {
        // create filter
        final var postFilterResponse = invokeRequest(HttpMethod.POST,
            "/restconf/data/ietf-subscribed-notifications:filters",
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "stream-filter": {
                    "name": "foo",
                    "stream-subtree-filter": {
                      "toaster:toasterOutOfBread": ""
                    }
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.CREATED, postFilterResponse.status());

        final var getFilterResponse = invokeRequest(HttpMethod.GET,
            "/restconf/data/ietf-subscribed-notifications:filters/stream-filter=foo",
            MediaTypes.APPLICATION_YANG_DATA_JSON, null, MediaTypes.APPLICATION_YANG_DATA_JSON);
        final var result = getFilterResponse.content().toString(StandardCharsets.UTF_8);
        assertEquals(HttpResponseStatus.OK, getFilterResponse.status());
        final var expectedFilter = """
            {
              "ietf-subscribed-notifications:stream-filter":[{
                "name":"foo"
              }]
            }""";
        JSONAssert.assertEquals(expectedFilter, result, JSONCompareMode.LENIENT);
    }

    @Disabled("FIXME fails to find toasterOutOfBread under stream-subtree-filter while POSTing JSON filter")
    @Test
    void writeJsonReadXmlSubtreeFilterTest() throws Exception {
        // create filter
        final var postFilterResponse = invokeRequest(HttpMethod.POST,
            "/restconf/data/ietf-subscribed-notifications:filters",
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "stream-filter": {
                    "name": "foo",
                    "stream-subtree-filter": {
                      "toaster:toasterOutOfBread": ""
                    }
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.CREATED, postFilterResponse.status());

        // get filter
        final var getFilterResponse = invokeRequest(HttpMethod.GET,
            "/restconf/data/ietf-subscribed-notifications:filters/stream-filter=foo",
            MediaTypes.APPLICATION_YANG_DATA_XML, null, MediaTypes.APPLICATION_YANG_DATA_XML);
        final var result = getFilterResponse.content().toString(StandardCharsets.UTF_8);
        assertEquals(HttpResponseStatus.OK, getFilterResponse.status());
        final var expectedFilter = """
            <stream-filter xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
              <name>foo</name><stream-subtree-filter>
              <toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"></toasterOutOfBread>
            </stream-subtree-filter></stream-filter>""";
        assertTrue(XMLUnit.compareXML(expectedFilter, result).identical());
    }

    @Test
    void writeXmlReadJsonSubtreeFilterTest() throws Exception {
        // create filter
        final var postFilterResponse = invokeRequest(HttpMethod.POST,
            "/restconf/data/ietf-subscribed-notifications:filters",
            MediaTypes.APPLICATION_YANG_DATA_XML,
            """
                <stream-filter xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
                 <name>foo</name>
                 <stream-subtree-filter>
                  <toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/>
                 </stream-subtree-filter>
                </stream-filter>""", MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.CREATED, postFilterResponse.status());

        final var getFilterResponse = invokeRequest(HttpMethod.GET,
            "/restconf/data/ietf-subscribed-notifications:filters/stream-filter=foo",
            MediaTypes.APPLICATION_YANG_DATA_JSON, null, MediaTypes.APPLICATION_YANG_DATA_JSON);
        final var result = getFilterResponse.content().toString(StandardCharsets.UTF_8);
        assertEquals(HttpResponseStatus.OK, getFilterResponse.status());
        final var expectedFilter = """
            {
              "ietf-subscribed-notifications:stream-filter":[{
                "name":"foo"
              }]
            }""";
        JSONAssert.assertEquals(expectedFilter, result, JSONCompareMode.LENIENT);
    }

    @Test
    void writeXmlReadXmlSubtreeFilterTest() throws Exception {
        // create filter
        final var postFilterResponse = invokeRequest(HttpMethod.POST,
            "/restconf/data/ietf-subscribed-notifications:filters",
            MediaTypes.APPLICATION_YANG_DATA_XML,
            """
                <stream-filter xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
                 <name>foo</name>
                 <stream-subtree-filter>
                  <toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/>
                 </stream-subtree-filter>
                </stream-filter>""", MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.CREATED, postFilterResponse.status());

        // get filter
        final var getFilterResponse = invokeRequest(HttpMethod.GET,
            "/restconf/data/ietf-subscribed-notifications:filters/stream-filter=foo",
            MediaTypes.APPLICATION_YANG_DATA_XML, null, MediaTypes.APPLICATION_YANG_DATA_XML);
        final var result = getFilterResponse.content().toString(StandardCharsets.UTF_8);
        assertEquals(HttpResponseStatus.OK, getFilterResponse.status());
        final var expectedFilter = """
            <stream-filter xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
              <name>foo</name><stream-subtree-filter>
              <toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/>
            </stream-subtree-filter></stream-filter>""";
        assertTrue(XMLUnit.compareXML(expectedFilter, result).identical());
    }
}
