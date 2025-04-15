/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opendaylight.restconf.nb.rfc8040.databind.AbstractBodyTest.loadFiles;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.JsonResourceBody;
import org.opendaylight.restconf.server.api.ResourceBody;
import org.opendaylight.restconf.server.api.XmlResourceBody;
import org.opendaylight.restconf.server.spi.ApiPathNormalizer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class NC1452Test {
    private static final String URI_PATH = "foo:foo/baz-list=delta";
    private static final LeafSetEntryNode<Object> EXPECTED_LEAF_SET_ENTRY = ImmutableNodes.newLeafSetEntryBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeWithValue<>(
            QName.create("urn:foo", "baz-list"), "delta"))
        .withValue("delta")
        .build();

    private static DatabindContext DATABIND;
    private static ApiPath APIPATH;

    @BeforeAll
    static void initModelContext() throws Exception {
        final var testFiles = loadFiles("/nt1452");
        DATABIND = DatabindContext.ofModel(YangParserTestUtils.parseYangFiles(testFiles));
        APIPATH = ApiPath.parse(URI_PATH);
    }

    @Test
    void testParseSingleLeafListToNormalizedNodeJson() throws Exception {
        final var parsedNormalizedNode = parse("""
            {
                "foo:baz-list": [
                    "delta"
                  ]
            }""", JsonResourceBody::new);
        assertEquals(EXPECTED_LEAF_SET_ENTRY, parsedNormalizedNode);
    }

    @Test
    void testParseSingleLeafListToNormalizedNodeXml() throws Exception {
        final var parsedNormalizedNode = parse("""
                <baz-list xmlns="urn:foo">delta</baz-list>
            """, XmlResourceBody::new);
        assertEquals(EXPECTED_LEAF_SET_ENTRY, parsedNormalizedNode);
    }

    private static NormalizedNode parse(final String body, final Function<InputStream, ResourceBody> resourceFunc)
            throws Exception {
        try (var resourceBody = resourceFunc.apply(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)))) {
            return resourceBody.toNormalizedNode(new ApiPathNormalizer(DATABIND).normalizeDataPath(APIPATH));
        }
    }
}
