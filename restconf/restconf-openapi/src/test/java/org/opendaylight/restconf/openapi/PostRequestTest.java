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
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.model.Path;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class PostRequestTest {
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
        .node(QName.create("", "nodes"))
        .node(QName.create("", "node"))
        .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private Map<String, Path> paths;

    @Before
    public void before() throws Exception {
        final var schemaService = mock(DOMSchemaService.class);
        final var context = YangParserTestUtils.parseYangResourceDirectory("/post");
        when(schemaService.getGlobalContext()).thenReturn(context);
        final var mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final var service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));
        final var openApi = new MountPointOpenApiGeneratorRFC8040(schemaService, service).getMountPointOpenApi();
        openApi.onMountPointCreated(INSTANCE_ID);
        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        final var mountPointApi = openApi.getMountPointApi(mockInfo, 1L, null);
        assertNotNull("Failed to find Datastore API", mountPointApi);
        paths = mountPointApi.paths();
        assertNotNull(paths);
    }

    @Test
    public void testMultiContainer() {
        final var root = paths.get("/rests/data/nodes/node=123/yang-ext:mount/post-model:root");
        assertNotNull(root);
        final var rootProp = getProperties(root);
        assertNotNull(rootProp);
        assertEquals(1, rootProp.size());
        final var innerCont = rootProp.get("inner-cont");
        assertNotNull(innerCont);
        final var innerContType = innerCont.get("type");
        assertNotNull(innerContType);
        assertEquals("object", innerContType.textValue());
        final var jsonNode = innerCont.get("properties");
        assertNotNull(jsonNode);
        assertTrue(jsonNode.isEmpty());
    }

    @Test
    public void testChoices() {
        final var firstContainer = paths.get("/rests/data/nodes/node=123/yang-ext:mount/post-model:first-container");
        assertNotNull(firstContainer);
        final var firstContainerProp = getProperties(firstContainer);
        assertEquals(1, firstContainerProp.size());
        final var leafDefault = firstContainerProp.get("leaf-default");
        assertNotNull(leafDefault);
        final var leafDefaultType = leafDefault.get("type");
        assertNotNull(leafDefaultType);
        assertEquals("integer", leafDefaultType.textValue());
        final var leafDefaultExample = leafDefault.get("example");
        assertNotNull(leafDefaultExample);
        assertEquals("10", leafDefaultExample.textValue());
    }

    @Test
    public void testDefaultChoice() {
        final var secondContainer = paths.get("/rests/data/nodes/node=123/yang-ext:mount/post-model:second-container");
        assertNotNull(secondContainer);
        final var secondContainerProp = getProperties(secondContainer);
        assertEquals(1, secondContainerProp.size());
        final var leafFirstCase = secondContainerProp.get("leaf-first-case");
        assertNotNull(leafFirstCase);
        final var leafFirstCaseType = leafFirstCase.get("type");
        assertNotNull(leafFirstCaseType);
        assertEquals("boolean", leafFirstCaseType.textValue());
        assertNotNull(leafFirstCase.get("example"));
    }

    @Test
    public void testRootList() {
        final var rootList = paths.get("/rests/data/nodes/node=123/yang-ext:mount/post-model:root-list={id}");
        assertNotNull(rootList);
        final var rootListProp = getProperties(rootList);
        assertEquals(1, rootListProp.size());
        final var idKey = rootListProp.get("id");
        assertNotNull(idKey);
        final var idKeyType = idKey.get("type");
        assertNotNull(idKeyType);
        assertEquals(idKeyType.textValue(), "string");
        assertNotNull(idKey.get("example"));
    }

    @Test
    public void testListInContainer() {
        final var cont = paths.get("/rests/data/nodes/node=123/yang-ext:mount/post-model:cont");
        assertNotNull(cont);
        final var contProp = getProperties(cont);
        assertEquals(1, contProp.size());
        final var list1 = contProp.get("list1");
        assertNotNull(list1);
        final var list1Type = list1.get("type");
        assertNotNull(list1Type);
        assertEquals(list1Type.textValue(), "array");
        final var list1Items = list1.get("items");
        assertNotNull(list1Items);
        final var list1ItemsType = list1Items.get("types");
        assertNotNull(list1ItemsType);
        assertEquals(list1ItemsType.textValue(), "object");
        final var list1ItemsProp = list1Items.get("properties");
        assertNotNull(list1ItemsProp);
        assertEquals(1, list1ItemsProp.size());
        final var name = list1ItemsProp.get("name");
        assertNotNull(name);
        final var nameType = name.get("type");
        assertNotNull(nameType);
        assertEquals(nameType.textValue(), "integer");
        assertNotNull(name.get("example"));
    }

    @Test
    public void testLeafList() {
        final var leafListContainer
            = paths.get("/rests/data/nodes/node=123/yang-ext:mount/post-model:leaf-list-container");
        assertNotNull(leafListContainer);
        final var leafListContainerProp = getProperties(leafListContainer);
        final var leafListNode = leafListContainerProp.get("leaf-list-node");
        assertNotNull(leafListNode);
        final var leafListNodeType = leafListNode.get("type");
        assertNotNull(leafListNodeType);
        assertEquals("array", leafListNodeType.textValue());
        final var leafListNodeItems = leafListNode.get("items");
        assertNotNull(leafListNodeItems);
        final var leafListNodeItemsType = leafListNodeItems.get("type");
        assertNotNull(leafListNodeItemsType);
        final var leafListNodeItemsValue = leafListNodeItemsType.textValue();
        assertNotNull(leafListNodeItemsValue);
        assertTrue(leafListNodeItemsValue.equals("boolean") || leafListNodeItemsValue.equals("integer"));
    }

    @Test
    public void testLeaf() {
        final var rootContainer = paths.get("/rests/data/nodes/node=123/yang-ext:mount/post-model:root-container");
        assertNotNull(rootContainer);
        final var rootContainerProp = getProperties(rootContainer);
        final var macAddress = rootContainerProp.get("mac-address");
        assertNotNull(macAddress);
        final var macAddressType = macAddress.get("type");
        assertNotNull(macAddressType);
        assertEquals("string", macAddressType.textValue());
        final var macAddressExample = macAddress.get("example");
        assertNotNull(macAddressExample);
        assertTrue(Pattern.matches("[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}", macAddressExample.textValue()));
    }

    private static JsonNode getProperties(final Path path) {
        final var post = path.post();
        assertNotNull(post);
        final var jsonNodes = post.requestBody();
        assertNotNull(jsonNodes);
        final var content = jsonNodes.get("content");
        assertNotNull(content);
        final var json = content.get("application/json");
        assertNotNull(json);
        final var schema = json.get("schema");
        assertNotNull(schema);
        final var properties = schema.get("properties");
        assertNotNull(properties);
        return properties;
    }
}
