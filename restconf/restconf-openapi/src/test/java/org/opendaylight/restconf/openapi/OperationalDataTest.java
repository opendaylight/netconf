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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class OperationalDataTest {
    private static final String DATA_MP_URI = "/rests/data/nodes/node=123/yang-ext:mount";
    private static final String OPERATIONS_MP_URI = "/rests/operations/nodes/node=123/yang-ext:mount";
    private static final Set<String> EXPECTED_SCHEMAS = Set.of(
        "action-types_config_container",
        "action-types_config_container_TOP",
        "action-types_config_list",
        "action-types_config_list_TOP",
        "action-types_config_multi-container",
        "action-types_config_multi-container_TOP",
        "action-types_container-action_input",
        "action-types_container-action_input_TOP",
        "action-types_container-action_output",
        "action-types_container-action_output_TOP",
        "action-types_list-action_input_TOP",
        "action-types_list-action_output_TOP",
        "action-types_list-action_output",
        "action-types_list-action_input",
        "action-types_multi-container_config_inner-container",
        "action-types_multi-container_config_inner-container_TOP",
        "operational_config_root",
        "operational_config_root_TOP",
        "operational_root_config_config-container",
        "operational_root_config_config-container_TOP",
        "operational_root_config-container_config_config-container-oper-list",
        "operational_root_config-container_config_config-container-oper-list_TOP",
        "operational_root_config_oper-container",
        "operational_root_config_oper-container_TOP",
        "operational_root_oper-container_config_config-container",
        "operational_root_oper-container_config_config-container_TOP",
        "operational_root_oper-container_config_oper-container-list",
        "operational_root_oper-container_config_oper-container-list_TOP");
    private static final Set<String> EXPECTED_PATHS = Set.of(
        OPERATIONS_MP_URI + "/action-types:list={name}/list-action",
        OPERATIONS_MP_URI + "/action-types:container/container-action",
        OPERATIONS_MP_URI + "/action-types:multi-container/inner-container/action",
        OPERATIONS_MP_URI,
        DATA_MP_URI + "/action-types:list={name}",
        DATA_MP_URI + "/operational:root",
        DATA_MP_URI + "/operational:root/oper-container/config-container",
        DATA_MP_URI + "/operational:root/oper-container/oper-container-list={oper-container-list-leaf}",
        DATA_MP_URI + "/action-types:multi-container",
        DATA_MP_URI + "/action-types:multi-container/inner-container",
        DATA_MP_URI + "/operational:root/oper-container",
        DATA_MP_URI + "/action-types:container",
        DATA_MP_URI + "/operational:root/config-container/config-container-oper-list={oper-container-list-leaf}",
        DATA_MP_URI + "/operational:root/config-container",
        DATA_MP_URI);
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
        .node(QName.create("", "nodes"))
        .node(QName.create("", "node"))
        .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private OpenApiObject mountPointApi;
    private Map<String, Schema> schemas;

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
        mountPointApi = openApi.getMountPointApi(mockInfo, 1L, null);
        assertNotNull("Failed to find Datastore API", mountPointApi);
        schemas = mountPointApi.components().schemas();
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
                // In case of 200 no content
                if (content != null) {
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
        assertEquals(EXPECTED_SCHEMAS, schemas.keySet());
    }

    @Test
    public void testOperationalConfigRootSchemaProperties() {
        final var configRoot = schemas.get("operational_config_root");
        assertNotNull(configRoot);
        final var actualProperties = getSetOfProperties(configRoot);
        assertEquals(Set.of("leaf-config", "config-container"), actualProperties);
    }

    @Test
    public void testOperationalConfigContOperListSchemaProperties() {
        final var configContOperList = schemas.get(
            "operational_root_config-container_config_config-container-oper-list");
        assertNotNull(configContOperList);
        final var actualProperties = getSetOfProperties(configContOperList);
        assertEquals(Set.of("oper-container-list-leaf"), actualProperties);
    }

    @Test
    public void testOperationalContListSchemaProperties() {
        final var operContList = schemas.get("operational_root_oper-container_config_oper-container-list");
        assertNotNull(operContList);
        final var actualProperties = getSetOfProperties(operContList);
        assertEquals(Set.of("oper-container-list-leaf"), actualProperties);
    }

    @Test
    public void testOperationalConConfigContSchemaProperties() {
        final var operConConfigCont = schemas.get("operational_root_oper-container_config_config-container");
        assertNotNull(operConConfigCont);
        final var actualProperties = getSetOfProperties(operConConfigCont);
        assertEquals(Set.of("config-container-config-leaf", "opconfig-container-oper-leaf"), actualProperties);
    }

    @Test
    public void testOperationalConfigContSchemaProperties() {
        final var configCont = schemas.get("operational_root_config_config-container");
        assertNotNull(configCont);
        final var actualProperties = getSetOfProperties(configCont);
        assertEquals(Set.of("config-container-config-leaf", "leaf-second-case"), actualProperties);
    }

    @Test
    public void testOperationalContSchemaProperties() {
        final var operCont = schemas.get("operational_root_config_oper-container");
        assertNotNull(operCont);
        final var actualProperties = getSetOfProperties(operCont);
        assertEquals(Set.of("config-container", "oper-container-list", "leaf-first-case", "oper-leaf-first-case",
            "oper-container-config-leaf-list"), actualProperties);
    }

    @Test
    public void testListActionSchemaProperties() {
        final var configRoot = schemas.get("action-types_config_list");
        assertNotNull(configRoot);
        final var actualProperties = getSetOfProperties(configRoot);
        assertEquals(Set.of("name"), actualProperties);
    }

    @Test
    public void testListActionInputSchemaProperties() {
        final var configRoot = schemas.get("action-types_list-action_input");
        assertNotNull(configRoot);
        final var actualProperties = getSetOfProperties(configRoot);
        assertEquals(Set.of("la-input"), actualProperties);
    }

    @Test
    public void testListActionOutputSchemaProperties() {
        final var configRoot = schemas.get("action-types_list-action_output");
        assertNotNull(configRoot);
        final var actualProperties = getSetOfProperties(configRoot);
        assertEquals(Set.of("la-output"), actualProperties);
    }

    @Test
    public void testContainerActionSchemaProperties() {
        final var configRoot = schemas.get("action-types_config_container");
        assertNotNull(configRoot);
        final var actualProperties = getSetOfProperties(configRoot);
        assertEquals(Set.of(), actualProperties);
    }

    @Test
    public void testContainerActionInputSchemaProperties() {
        final var configRoot = schemas.get("action-types_container-action_input");
        assertNotNull(configRoot);
        final var actualProperties = getSetOfProperties(configRoot);
        assertEquals(Set.of("ca-input"), actualProperties);
    }

    @Test
    public void testContainerActionOutputSchemaProperties() {
        final var configRoot = schemas.get("action-types_container-action_output");
        assertNotNull(configRoot);
        final var actualProperties = getSetOfProperties(configRoot);
        assertEquals(Set.of("ca-output"), actualProperties);
    }

    private static void verifyOperationHaveCorrectReference(final JsonNode jsonNode) {
        final var schema = jsonNode.get("schema");
        final var ref = schema.get("$ref");
        // In case of a POST RPC with a direct input body and no reference value
        if (ref != null) {
            final var refValue = ref.textValue();
            final var schemaElement = refValue.substring(refValue.lastIndexOf("/") + 1);
            assertTrue("Reference [" + refValue + "] not found in EXPECTED Schemas",
                EXPECTED_SCHEMAS.contains(schemaElement));
        } else {
            final var type = schema.get("type");
            assertNotNull(type);
            assertEquals("object", type.asText());
        }
    }

    private static Set<String> getSetOfProperties(final Schema schema) {
        final var fieldNames = schema.properties().fieldNames();
        final var fieldNamesSpliterator = Spliterators.spliteratorUnknownSize(fieldNames, Spliterator.ORDERED);
        return StreamSupport.stream(fieldNamesSpliterator, false)
            .collect(Collectors.toSet());
    }
}
