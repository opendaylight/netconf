/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.MediaTypes;

public class FilterWritingTest extends AbstractNotificationSubscriptionTest {
    @Disabled("FIXME fails to find toasterOutOfBread under stream-subtree-filter while POSTing JSON filter")
    @Test
    void writeJsonSubtreeFilterTest() throws Exception {
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
        // check response
        assertEquals(HttpResponseStatus.CREATED, postFilterResponse.status());
    }

    @Test
    void writeXmlSubtreeFilterTest() throws Exception {
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
        // check response
        assertEquals(HttpResponseStatus.CREATED, postFilterResponse.status());
    }
}
