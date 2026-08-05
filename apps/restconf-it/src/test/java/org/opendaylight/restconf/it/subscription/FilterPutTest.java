/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Filters;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.binfmt.NormalizedNodeStreamVersion;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/**
 * YANGTOOLS-1670 reproducer: PUT of an {@code ietf-subscribed-notifications} {@code stream-filter} carrying a
 * {@code stream-subtree-filter}, which is an {@code anydata} node. Each request is read back in the opposite encoding,
 * so the two round-trip tests together cover all four parse/write combinations of the {@code anydata} body.
 *
 * <p>The two encodings are not equally capable. The XML parser produces a {@code DOMSourceAnydata} body and the XML
 * writer accepts it, whereas the JSON writer accepts only {@code NormalizedAnydata} and the JSON parser has no
 * {@code anydata} support at all.
 *
 * <p>{@link #putXmlSubtreeFilterBinfmtTest()} covers the third encoding: binfmt, which a clustered datastore uses to
 * turn each commit into a {@code CommitTransactionPayload} for its Raft journal. binfmt rejects {@code anydata}
 * outright, for every object model.
 */
class FilterPutTest extends AbstractNotificationSubscriptionTest {
    private static final String URI = "/rests/data/ietf-subscribed-notifications:filters/stream-filter=foo";
    private static final YangInstanceIdentifier FILTERS = YangInstanceIdentifier.of(Filters.QNAME);
    private static final String FILTER_XML = """
        <stream-filter xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
         <name>foo</name>
         <stream-subtree-filter>
          <toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/>
         </stream-subtree-filter>
        </stream-filter>""";
    private static final String FILTER_JSON = """
        {
          "ietf-subscribed-notifications:stream-filter": [
            {
              "name": "foo",
              "stream-subtree-filter": {
                "toaster:toasterOutOfBread": ""
              }
            }
          ]
        }""";

    @BeforeAll
    static void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    void putXmlSubtreeFilterTest() throws Exception {
        final var putFilterResponse = invokeRequest(HttpMethod.PUT, URI, MediaTypes.APPLICATION_YANG_DATA_XML,
            MediaTypes.APPLICATION_YANG_DATA_JSON, FILTER_XML);
        assertEquals(HttpResponseStatus.CREATED, putFilterResponse.status());

        final var getFilterResponse = invokeRequest(HttpMethod.GET, URI, MediaTypes.APPLICATION_YANG_DATA_JSON,
            MediaTypes.APPLICATION_YANG_DATA_JSON, null);
        assertEquals(HttpResponseStatus.OK, getFilterResponse.status());
        JSONAssert.assertEquals(FILTER_JSON, getFilterResponse.content().toString(StandardCharsets.UTF_8),
            JSONCompareMode.LENIENT);
    }

    @Test
    void putJsonSubtreeFilterTest() throws Exception {
        final var putFilterResponse = invokeRequest(HttpMethod.PUT, URI, MediaTypes.APPLICATION_YANG_DATA_JSON,
            MediaTypes.APPLICATION_YANG_DATA_JSON, FILTER_JSON);
        assertEquals(HttpResponseStatus.CREATED, putFilterResponse.status());

        final var getFilterResponse = invokeRequest(HttpMethod.GET, URI, MediaTypes.APPLICATION_YANG_DATA_XML,
            MediaTypes.APPLICATION_YANG_DATA_XML, null);
        assertEquals(HttpResponseStatus.OK, getFilterResponse.status());
        assertTrue(XMLUnit.compareXML(FILTER_XML, getFilterResponse.content().toString(StandardCharsets.UTF_8))
            .identical());
    }

    @Test
    void putXmlSubtreeFilterBinfmtTest() throws Exception {
        final var putFilterResponse = invokeRequest(HttpMethod.PUT, URI, MediaTypes.APPLICATION_YANG_DATA_XML,
            MediaTypes.APPLICATION_YANG_DATA_JSON, FILTER_XML);
        assertEquals(HttpResponseStatus.CREATED, putFilterResponse.status());

        final NormalizedNode filters;
        try (var tx = getDomBroker().newReadOnlyTransaction()) {
            filters = tx.read(LogicalDatastoreType.CONFIGURATION, FILTERS).get().orElseThrow();
        }

        // The datastore used in this test is in-memory: it just keeps NormalizedNode objects around, and never
        // turns them into bytes. A real clustered datastore must do exactly that on every commit -- it encodes the
        // change with binfmt, so it can be written to the Raft journal and replicated to the other cluster members.
        // That encoding is where anydata fails, so we run it by hand here. Without it, this test would pass.
        try (var out = NormalizedNodeStreamVersion.current()
                .newDataOutput(new DataOutputStream(new ByteArrayOutputStream()))) {
            out.writeNormalizedNode(filters);
        }
    }
}
