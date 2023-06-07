/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.mountpoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(Parameterized.class)
public final class CustomBasePathOpenApiTest {

    private static final String DEFAULT_BASE_PATH = "rests";
    private static final String CUSTOM_BASE_PATH = "restconf";
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();
    private static final String INSTANCE_URL = "/nodes/node=123/";
    private static EffectiveModelContext context;
    private static DOMSchemaService schemaService;
    private MountPointOpenApi openApi;

    @Parameter(0)
    public static BiFunction<DOMSchemaService, DOMMountPointService, MountPointOpenApiGeneratorRFC8040> newMountPoint;
    @Parameter(1)
    public static String basePath;

    @Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(
            new Object[]{
                (BiFunction<DOMSchemaService, DOMMountPointService, MountPointOpenApiGeneratorRFC8040>)
                    MountPointOpenApiGeneratorRFC8040::new, DEFAULT_BASE_PATH
            },
            new Object[]{
                (BiFunction<DOMSchemaService, DOMMountPointService, MountPointOpenApiGeneratorRFC8040>)
                    (schemaServiceArg, serviceArg) ->
                        new MountPointOpenApiGeneratorRFC8040(schemaServiceArg, serviceArg, CUSTOM_BASE_PATH),
                CUSTOM_BASE_PATH
            }
        );
    }

    @BeforeClass
    public static void beforeClass() {
        schemaService = mock(DOMSchemaService.class);
        context = YangParserTestUtils.parseYangResourceDirectory("/NETCONF-1021");
        when(schemaService.getGlobalContext()).thenReturn(context);
    }

    @Before
    public void before() {
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));
        final DOMMountPointService service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));
        openApi = newMountPoint.apply(schemaService, service).getMountPointOpenApi();
    }

    @Test
    public void testOperationPathsWithBasePath() throws Exception {
        final UriInfo mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);
        final OpenApiObject mountPointApi = openApi.getMountPointApi(mockInfo, 1L, "custom-base-path-test",
            "2023-05-30");
        assertNotNull("Failed to find Datastore API", mountPointApi);

        final Set<String> paths = mountPointApi.paths().keySet();

        final String containerOperationRoot = "/" + basePath + "/operations" + INSTANCE_URL + "yang-ext:mount";
        final String containerDataRoot = "/" + basePath + "/data" + INSTANCE_URL + "yang-ext:mount";
        Set<String> expectedPaths = Set.of(
            containerOperationRoot + "/custom-base-path-test:rpc-call",
            containerOperationRoot + "/custom-base-path-test:top/top-action",
            containerOperationRoot + "/custom-base-path-test:top/mid/mid-action",
            containerOperationRoot + "/custom-base-path-test:top/mid/bottom/bottom-action",
            containerDataRoot + "/custom-base-path-test:top/mid",
            containerDataRoot + "/custom-base-path-test:top/top-list={topListKey}",
            containerDataRoot + "/custom-base-path-test:top",
            containerDataRoot + "/custom-base-path-test:top/mid/mid-list={midListKey}",
            containerDataRoot + "/custom-base-path-test:top/mid/bottom/bottom-list={bottomListKey}",
            containerDataRoot + "/custom-base-path-test:top/mid/bottom",
            containerDataRoot);
        assertEquals("Unexpected paths", expectedPaths, paths);
    }

}
