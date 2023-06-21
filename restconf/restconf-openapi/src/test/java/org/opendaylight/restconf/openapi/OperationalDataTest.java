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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class OperationalDataTest {
    private static final Set<String> EXPECTED_SCHEMAS = Set.of(
        "operational_root_config_config-container_TOP",
        "operational_config_root",
        "action-types_config_list",
        "action-types_container-action_input",
        "action-types_multi-container_config_inner-container",
        "action-types_list-action_input_TOP",
        "action-types_multi-container_config_inner-container_TOP",
        "action-types_config_list_TOP",
        "action-types_container-action_output",
        "operational_root_config_config-container",
        "action-types_list-action_input",
        "action-types_list-action_output_TOP",
        "action-types_container-action_input_TOP",
        "operational_config_root_TOP",
        "action-types_list-action_output",
        "action-types_container-action_output_TOP",
        "action-types_config_multi-container_TOP",
        "action-types_config_container",
        "action-types_config_container_TOP",
        "action-types_config_multi-container");
    private static final Set<String> EXPECTED_PATHS = Set.of(
        "/rests/operations/nodes/node=123/yang-ext:mount/action-types:list={name}/list-action",
        "/rests/operations/nodes/node=123/yang-ext:mount/action-types:container/container-action",
        "/rests/operations/nodes/node=123/yang-ext:mount",
        "/rests/data/nodes/node=123/yang-ext:mount/action-types:list={name}",
        "/rests/data/nodes/node=123/yang-ext:mount/action-types:container",
        "/rests/data/nodes/node=123/yang-ext:mount/action-types:multi-container",
        "/rests/operations/nodes/node=123/yang-ext:mount/action-types:multi-container/inner-container/action",
        "/rests/data/nodes/node=123/yang-ext:mount/operational:root/config-container",
        "/rests/data/nodes/node=123/yang-ext:mount/action-types:multi-container/inner-container",
        "/rests/data/nodes/node=123/yang-ext:mount",
        "/rests/data/nodes/node=123/yang-ext:mount/operational:root");
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
        .node(QName.create("", "nodes"))
        .node(QName.create("", "node"))
        .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private OpenApiObject mountPointApi;

    @Before
    public void before() throws Exception {
        final var schemaService = mock(DOMSchemaService.class);
        final var context = YangParserTestUtils.parseYangResourceDirectory("/operational");
        when(schemaService.getGlobalContext()).thenReturn(context);
        final var mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final var service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));
        final var openApi = new MountPointOpenApiGeneratorRFC8040(schemaService, service).getMountPointOpenApi();
        openApi.onMountPointCreated(INSTANCE_ID);
        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        mountPointApi = openApi.getMountPointApi(mockInfo, 1L, Optional.empty());
        assertNotNull("Failed to find Datastore API", mountPointApi);
    }

    @Test
    public void testOperationalPath() {
        final var paths = mountPointApi.paths();
        assertEquals(EXPECTED_PATHS, paths.keySet());
        for (final var path : paths.values()) {
            if (path.get() != null) {
                final var responses = path.get().responses();
                final var response = responses.elements().next();
                final var content = response.get("content");
                if (content != null) {
                    // In case of 200 no content
                    verifyOperationHaveCorrectReference(content.get("application/xml"));
                    verifyOperationHaveCorrectReference(content.get("application/json"));
                }
            }
            if (path.put() != null) {
                final var responses = path.put().requestBody();
                final var content = responses.get("content");
                verifyOperationHaveCorrectReference(content.get("application/xml"));
                verifyOperationHaveCorrectReference(content.get("application/json"));
            }
            if (path.post() != null) {
                final var responses = path.post().requestBody();
                final var content = responses.get("content");
                verifyOperationHaveCorrectReference(content.get("application/xml"));
                verifyOperationHaveCorrectReference(content.get("application/json"));
            }
            if (path.patch() != null) {
                final var responses = path.patch().requestBody();
                final var content = responses.get("content");
                verifyOperationHaveCorrectReference(content.get("application/yang-data+xml"));
                verifyOperationHaveCorrectReference(content.get("application/yang-data+json"));
            }
        }
    }

    @Test
    public void testOperationalSchema() {
        final var schemas = mountPointApi.components().schemas();
        assertEquals(EXPECTED_SCHEMAS, schemas.keySet());
    }

    private void verifyOperationHaveCorrectReference(final JsonNode jsonNode) {
        final var schema = jsonNode.get("schema");
        final var ref = schema.get("$ref");
        if (ref != null) {
            // in case of POST RPC with direct input body without ref value.
            final var refValue = ref.textValue();
            final var schemaElement = refValue.substring(refValue.lastIndexOf("/") + 1);
            assertTrue(EXPECTED_SCHEMAS.contains(schemaElement));
        }
    }
}
