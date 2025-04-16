/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import java.util.Optional;
import javax.ws.rs.core.MultivaluedHashMap;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class NC1451Test extends AbstractRestconfTest {
    private static final EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/nc1451");
    private static final YangInstanceIdentifier BAZ_LIST_IDENTIFIER = YangInstanceIdentifier.of(
        new NodeIdentifier(QName.create("urn:foo", "foo")),
        new NodeIdentifier(QName.create("urn:foo", "baz-list")),
        new NodeWithValue<>(QName.create("urn:foo", "baz-list"), "delta"));
    private static final LeafSetEntryNode<Object> LEAF_SET_ENTRY_NODE = ImmutableNodes.newLeafSetEntryBuilder()
        .withNodeIdentifier(new NodeWithValue<>(QName.create("urn:foo", "baz-list"), "delta"))
        .withValue("delta")
        .build();
    private static final ApiPath API_PATH = apiPath("foo:foo/baz-list=delta");

    @Mock
    private DOMDataTreeReadTransaction tx;

    @BeforeEach
    void setupUriInfo() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(tx).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_ENTRY_NODE)))
            .when(tx)
            .read(LogicalDatastoreType.CONFIGURATION, BAZ_LIST_IDENTIFIER);
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_ENTRY_NODE)))
            .when(tx)
            .read(LogicalDatastoreType.OPERATIONAL, BAZ_LIST_IDENTIFIER);
    }

    @Override
    EffectiveModelContext modelContext() {
        return MODEL_CONTEXT;
    }

    @Test
    void testGetSpecificLeafListJsonData() {
        // Send request to get a LeafSetEntryNode.
        final var body = assertNormalizedBody(200, ar -> restconf.dataGET(API_PATH, uriInfo, sc, ar));

        // Verify that the response is correctly mapped to JSON.
        assertFormat("""
            {
              "foo:baz-list": [
                "delta"
              ]
            }""", body::formatToJSON, true);
    }

    @Test
    void testGetSpecificLeafListXmlData() {
        // Send request to get a LeafSetEntryNode.
        final var body = assertNormalizedBody(200, ar -> restconf.dataGET(API_PATH, uriInfo, sc, ar));

        // Verify that the response is correctly mapped to XML.
        assertFormat("""
            <baz-list xmlns="urn:foo">delta</baz-list>""", body::formatToXML, true);
    }
}
