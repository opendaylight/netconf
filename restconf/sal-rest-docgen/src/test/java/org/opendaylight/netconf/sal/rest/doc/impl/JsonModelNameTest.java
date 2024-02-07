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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.ws.rs.core.UriInfo;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.mountpoints.MountPointSwagger;
import org.opendaylight.netconf.sal.rest.doc.swagger.OpenApiObject;
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
        final MountPointSwagger swagger = new MountPointSwaggerGeneratorRFC8040(schemaService, service, "rests")
            .getMountPointSwagger();
        swagger.onMountPointCreated(INSTANCE_ID);

        mountPointApi = (OpenApiObject) swagger.getMountPointApi(mockInfo, 1L, "toaster2", "2009-11-20",
            ApiDocServiceImpl.OAversion.V3_0);
        assertNotNull("Failed to find MountPoint API", mountPointApi);
    }

    @Test
    public void testIfToasterRequestContainsCorrectModelName() {
        final var toaster = mountPointApi.getComponents().getSchemas().path("toaster2_toaster_TOP");
        assertFalse(toaster.path("properties").path("toaster2:toaster").isMissingNode());
    }

    @Test
    public void testIfFirstNodeInJsonPayloadContainsCorrectModelName() {
        final var schemas = mountPointApi.getComponents().getSchemas();
        final var paths = mountPointApi.getPaths().fields();
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
        final var reference = put.path("requestBody").path("content").path("application/json").path("schema")
            .path("$ref").textValue();

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