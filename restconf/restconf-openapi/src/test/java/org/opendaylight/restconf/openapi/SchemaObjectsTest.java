/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.UriInfo;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.model.MediaTypeObject;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class SchemaObjectsTest {
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("" , "id"), "123").build();

    private static OpenApiObject mountPointApi;

    @BeforeClass
    public static void beforeClass() throws Exception {
        final var schemaService = mock(DOMSchemaService.class);
        final var context = YangParserTestUtils.parseYangResourceDirectory("/yang");
        when(schemaService.getGlobalContext()).thenReturn(context);
        final UriInfo mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        final DOMMountPointService service = mock(DOMMountPointService.class);
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));
        final var openApi = new MountPointOpenApiGeneratorRFC8040(schemaService, service).getMountPointOpenApi();
        openApi.onMountPointCreated(INSTANCE_ID);

        mountPointApi = openApi.getMountPointApi(mockInfo, 1L, null);
        assertNotNull("Failed to find MountPoint API", mountPointApi);
    }

    @Test
    public void testIfOperationsUseOneSchema() {
        final var schemas = mountPointApi.components().schemas();
        for (final var pathsEntry : mountPointApi.paths().entrySet()) {
            final var path = pathsEntry.getValue();
            if (path.post() == null || path.put() == null || path.patch() == null || path.delete() == null) {
                // skip operational data
                continue;
            }
            for (final var operation : List.of(path.put(), path.patch(), path.post())) {
                final var schema = schemas.get(extractSchemaName(operation));
                assertNotNull("Schema for \"" + operation + "\" is missing.", schema);
            }
        }
    }

    /**
     * Extract schema name used for operation.
     * <p>
     * We assume that for all content types of operation (XML, JSON) the same schema is used. We extract its name from
     * the schema reference used in operation.
     * </p>
     * @param operation for which we want to find schema
     * @return name of the schema used for operation
     */
    private static String extractSchemaName(final Operation operation) {
        // Find distinct schema refs
        final var references = operation.requestBody().content().values().stream()
            .map(MediaTypeObject::schema)
            .map(SchemaObjectsTest::getRef)
            .distinct()
            .toList();
        // Assert all schema refs are same
        assertEquals("Inconsistent schemas for operation: " + operation.summary(), 1, references.size());
        return references.get(0).replaceAll("#/components/schemas/", "");
    }

    private static String getRef(final Schema schema) {
        final String ref;
        if (schema.ref() != null) {
            ref = schema.ref();
        } else {
            final var properties = schema.properties();
            if (properties != null && !properties.isEmpty()) {
                final var property = schema.properties().values().iterator().next();
                if (property.type().equals("array")) {
                    ref = property.items().ref();
                } else {
                    ref = property.ref();
                }
            } else {
                ref = null;
            }
        }
        return ref;
    }
}
