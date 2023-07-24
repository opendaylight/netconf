/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Pattern;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.Revision;

public class JsonModelNameTest extends AbstractApiDocTest {
    private static final String NAME = "toaster2";
    private static final String REVISION_DATE = "2009-11-20";

    private final ApiDocGeneratorRFC8040 generator = new ApiDocGeneratorRFC8040(SCHEMA_SERVICE);

    @Test
    public void testIfToasterRequestContainsCorrectModelName() {
        final var module = CONTEXT.findModule(NAME, Revision.of(REVISION_DATE)).orElseThrow();
        final var doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
            ApiDocServiceImpl.OAversion.V2_0);
        final var toaster = doc.getDefinitions().path("toaster2_toaster_TOP");
        assertFalse(toaster.path("properties").path("toaster2:toaster").isMissingNode());
    }

    @Test
    public void testIfFirstNodeInJsonPayloadContainsCorrectModelName() {
        final var module = CONTEXT.findModule(NAME, Revision.of(REVISION_DATE)).orElseThrow();
        final var doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
            ApiDocServiceImpl.OAversion.V2_0);
        final var schemas = doc.getDefinitions();
        final var paths = doc.getPaths().fields();
        while (paths.hasNext()) {
            final var stringPathEntry = paths.next();
            final var put = stringPathEntry.getValue().path("put");
            if (!put.isMissingNode()) {
                final var schemaReference = getSchemaJsonReference(put);
                assertNotNull("PUT reference for [" + put + "] is in wrong format", schemaReference);
                final var tested = schemas.get(schemaReference);
                assertNotNull("Reference for [" + put + "] was not found", tested);
                final var nodeName = tested.path("properties").fields().next().getKey();
                final var key = stringPathEntry.getKey();
                final var expectedModuleName = extractModuleName(key);
                assertTrue(nodeName.contains(expectedModuleName));
            }
        }
    }

    private static String getSchemaJsonReference(final JsonNode put) {
        final var reference = put.path("parameters").findValue("$ref").textValue();

        final var lastSlashIndex = reference.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < reference.length() - 1) {
            return reference.substring(lastSlashIndex + 1);
        }
        return null; // Return null if there is no string after the last "/"
    }

    /**
     * Return last module name for path in provided input.
     * <p>
     * For example if input looks like this:
     * `/rests/data/nodes/node=123/yang-ext:mount/mandatory-test:root-container/optional-list={id}/data2:data`
     * then returned string should look like this: `data2:`.
     * </p>
     * @param input String URI path
     * @return last module name in URI
     */
    private static String extractModuleName(final String input) {
        final var pattern = Pattern.compile("\\b([^/:]+):\\b");
        final var matcher = pattern.matcher(input);

        String result = null;
        while (matcher.find()) {
            result = matcher.group(1) + ":";
        }
        return result;
    }
}