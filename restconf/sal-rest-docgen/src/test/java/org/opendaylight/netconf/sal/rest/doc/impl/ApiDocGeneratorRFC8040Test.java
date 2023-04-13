/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.opendaylight.netconf.sal.rest.doc.openapi.OpenApiObject;
import org.opendaylight.yangtools.yang.common.Revision;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class ApiDocGeneratorRFC8040Test extends AbstractApiDocTest {
    private static final String NAME = "toaster2";
    private static final String REVISION_DATE = "2009-11-20";
    private static final String NAME_2 = "toaster";
    private static final String REVISION_DATE_2 = "2009-11-20";

    private final ApiDocGeneratorRFC8040 generator = new ApiDocGeneratorRFC8040(SCHEMA_SERVICE);

    /**
     * Test that paths are generated according to the model.
     */
    @Test
    public void testPaths() {
        final var module = CONTEXT.findModule(NAME, Revision.of(REVISION_DATE)).orElseThrow();
        final OpenApiObject doc = generator.getOpenApiDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT);

        assertEquals(List.of("/rests/data",
            "/rests/data/toaster2:toaster",
            "/rests/data/toaster2:toaster/toasterSlot={slotId}",
            "/rests/data/toaster2:toaster/toasterSlot={slotId}/toaster-augmented:slotInfo",
            "/rests/data/toaster2:lst",
            "/rests/data/toaster2:lst/cont1",
            "/rests/data/toaster2:lst/cont1/cont11",
            "/rests/data/toaster2:lst/cont1/lst11",
            "/rests/data/toaster2:lst/lst1={key1},{key2}",
            "/rests/operations/toaster2:make-toast",
            "/rests/operations/toaster2:cancel-toast",
            "/rests/operations/toaster2:restock-toaster"),
            ImmutableList.copyOf(doc.getPaths().fieldNames()));
    }

    /**
     * Test that generated configuration paths allow to use operations: get, put, patch, delete and post.
     */
    @Test
    public void testConfigPaths() {
        final List<String> configPaths = List.of("/rests/data/toaster2:lst",
                "/rests/data/toaster2:lst/cont1",
                "/rests/data/toaster2:lst/cont1/cont11",
                "/rests/data/toaster2:lst/cont1/lst11",
                "/rests/data/toaster2:lst/lst1={key1},{key2}");

        final var module = CONTEXT.findModule(NAME, Revision.of(REVISION_DATE)).orElseThrow();
        final OpenApiObject doc = generator.getOpenApiDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT);

        for (final String path : configPaths) {
            final JsonNode node = doc.getPaths().get(path);
            assertFalse(node.path("get").isMissingNode());
            assertFalse(node.path("put").isMissingNode());
            assertFalse(node.path("delete").isMissingNode());
            assertFalse(node.path("post").isMissingNode());
            assertFalse(node.path("patch").isMissingNode());
        }
    }
}
