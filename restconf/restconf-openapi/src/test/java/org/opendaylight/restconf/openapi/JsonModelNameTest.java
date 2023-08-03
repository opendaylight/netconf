/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.regex.Pattern;
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
    public void testIfToasterRequestContainsCorrectModelName() {
        final var schemas = mountPointApi.components().schemas();
        final var toaster = schemas.get("toaster2_toaster_TOP");
        assertNotNull(toaster.properties().get("toaster2:toaster"));
    }

    @Test
    public void testIfFirstNodeInJsonPayloadContainsCorrectModelName() {
        for (final var stringPathEntry : mountPointApi.paths().entrySet()) {
            final var value = stringPathEntry.getValue();
            if (value.put() != null) {
                final var moduleName = getSchemaPutOperationModuleName(value.put());
                assertNotNull("PUT module name for [" + value.put() + "] is in wrong format", moduleName);
                final var key = stringPathEntry.getKey();
                final var expectedModuleName = extractModuleName(key);
                assertTrue(moduleName.contains(expectedModuleName));
            }
        }
    }

    private static String getSchemaPutOperationModuleName(final Operation put) {
        final var parentName  = put.requestBody().path("content").path("application/json").path("schema")
            .path("properties").properties().iterator().next().getKey();

        final var doubleDotsIndex = parentName.indexOf(':');
        if (doubleDotsIndex >= 0 && doubleDotsIndex < parentName.length() - 1) {
            return parentName.substring(0, doubleDotsIndex + 1);
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
