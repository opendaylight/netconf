/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
import org.opendaylight.netconf.sal.rest.doc.mountpoints.MountPointSwagger;
import org.opendaylight.netconf.sal.rest.doc.swagger.OpenApiObject;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(Parameterized.class)
public final class CustomOpenApiBasePathTest {
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private MountPointSwagger swagger;

    @Parameter
    public static String basePath;

    @Parameters(name = "{0}")
    public static Collection<String> data() {
        return List.of("rests", "restconf");
    }

    @Before
    public void before() {
        final var schemaService = mock(DOMSchemaService.class);
        final var context = YangParserTestUtils.parseYangResource("/yang/action-types.yang");

        when(schemaService.getGlobalContext()).thenReturn(context);
        final var mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));
        final var mountPointService = mock(DOMMountPointService.class);
        when(mountPointService.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));
        final var generator = new MountPointSwaggerGeneratorRFC8040(schemaService, mountPointService, basePath);
        swagger = generator.getMountPointSwagger();
        swagger.onMountPointCreated(INSTANCE_ID);
    }

    @Test
    public void testCustomOpenApiBasePath() throws Exception {
        final var mockInfo = DocGenTestHelper.createMockUriInfo("http://localhost/path");
        final var mountPointApi = (OpenApiObject) swagger.getMountPointApi(mockInfo, 1L, "action-types", null,
                OAversion.V3_0);
        assertNotNull("Failed to find Datastore API", mountPointApi);

        final var containerOperationRoot = "/" + basePath + "/operations/nodes/node=123/yang-ext:mount";
        final var containerDataRoot = "/" + basePath + "/data/nodes/node=123/yang-ext:mount";
        final var expectedPaths = Set.of(
                containerOperationRoot + "/action-types:list={name}/list-action",
                containerOperationRoot + "/action-types:container/container-action",
                containerOperationRoot + "/action-types:multi-container/inner-container/action",
                containerDataRoot + "/action-types:list={name}",
                containerDataRoot + "/action-types:container",
                containerDataRoot + "/action-types:multi-container",
                containerDataRoot + "/action-types:multi-container/inner-container",
                containerDataRoot);
        final Set<String> actualPaths = new HashSet<>(mountPointApi.getPaths().size());
        mountPointApi.getPaths().fieldNames().forEachRemaining(actualPaths::add);
        assertEquals("Unexpected paths", expectedPaths, actualPaths);
    }
}
