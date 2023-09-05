/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
import javax.ws.rs.core.UriInfo;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class JsonModelNameTest {
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
        .node(QName.create("", "nodes"))
        .node(QName.create("", "node"))
        .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

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
    public void testIfFirstNodeInJsonPayloadContainsCorrectModelName() {
        for (final var stringPathEntry : mountPointApi.paths().entrySet()) {
            final var put = stringPathEntry.getValue().put();
            if (put != null) {
                final var moduleName = getSchemaPutOperationModuleName(put);
                assertNotNull("PUT module name for [" + put + "] is in wrong format", moduleName);
                final var key = stringPathEntry.getKey();
                final var expectedModuleName = extractModuleName(key);
                assertTrue(moduleName.contains(expectedModuleName));
            }
        }
    }

    private static String getSchemaPutOperationModuleName(final Operation put) {
        final var parentName  = put.requestBody().content().path("application/json").path("schema")
            .path("properties").properties().iterator().next().getKey();

        final var doubleDotsIndex = parentName.indexOf(':');
        if (doubleDotsIndex >= 0 && doubleDotsIndex < parentName.length() - 1) {
            return parentName.substring(0, doubleDotsIndex + 1);
        }
        return null; // Return null if there is no string after the last "/"
    }

    /**
     * Return last module name in provided path.
     * <p>
     * For example if path looks like this:
     * `/rests/data/nodes/node=123/yang-ext:mount/mandatory-test:root-container/optional-list={id}/data2:data`
     * then returned string should look like this: `data2`.
     * </p>
     * @param path String URI path
     * @return last module name in URI
     */
    private static String extractModuleName(final String path) {
        final var components = Arrays.stream(path.split("/"))
            .filter(c -> c.contains(":"))
            .toList();
        assertFalse("No module name found in path: " + path, components.isEmpty());
        return components.get(components.size() - 1).split(":")[0];
    }
}
