/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Filters;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.binfmt.NormalizedNodeStreamVersion;

/**
 * ODLC-940 reproducer: PUT of an {@code ietf-subscribed-notifications} {@code stream-filter} carrying a
 * {@code stream-subtree-filter}, which is an {@code anydata} node.
 *
 * <p>The PUT itself is accepted. The failure happens later, when a clustered datastore serializes the resulting
 * {@code DataTreeCandidate} into a {@code CommitTransactionPayload} for its journal: binfmt has no {@code anydata}
 * support, so {@code NormalizedNodeWriter} throws {@code IllegalStateException}. The in-memory datastore backing this
 * harness keeps {@link NormalizedNode}s as-is and never serializes them, hence the serialization is driven explicitly
 * here.
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

    @Test
    void putXmlSubtreeFilterTest() throws Exception {
        final var putFilterResponse = invokeRequest(HttpMethod.PUT, URI, MediaTypes.APPLICATION_YANG_DATA_XML,
            MediaTypes.APPLICATION_YANG_DATA_JSON, FILTER_XML);
        assertEquals(HttpResponseStatus.CREATED, putFilterResponse.status());

//        serializeStoredFilters();
    }

    @Test
    void putJsonSubtreeFilterTest() throws Exception {
        final var putFilterResponse = invokeRequest(HttpMethod.PUT, URI, MediaTypes.APPLICATION_YANG_DATA_JSON,
            MediaTypes.APPLICATION_YANG_DATA_JSON, FILTER_JSON);
        assertEquals(HttpResponseStatus.CREATED, putFilterResponse.status());

//        serializeStoredFilters();
    }

    /**
     * Serialize the stored {@code filters} subtree the way a clustered datastore does while creating its commit
     * payload.
     */
    private void serializeStoredFilters() throws Exception {
        final NormalizedNode filters;
        try (var tx = getDomBroker().newReadOnlyTransaction()) {
            filters = tx.read(LogicalDatastoreType.CONFIGURATION, FILTERS).get().orElseThrow();
        }

        try (var out = NormalizedNodeStreamVersion.current()
                .newDataOutput(new DataOutputStream(new ByteArrayOutputStream()))) {
            out.writeNormalizedNode(filters);
        }
    }
}
