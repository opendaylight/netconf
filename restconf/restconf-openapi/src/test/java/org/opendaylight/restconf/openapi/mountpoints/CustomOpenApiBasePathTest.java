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

import java.util.Collection;
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
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(Parameterized.class)
public final class CustomOpenApiBasePathTest {
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private MountPointOpenApi openApi;

    @Parameter
    public static String basePath;

    @Parameters(name = "{0}")
    public static Collection<String> data() {
        return List.of("rests", "restconf");
    }

    @Before
    public void before() {
        final var schemaService = mock(DOMSchemaService.class);
        final var context = YangParserTestUtils.parseYang("""
            module custom-base-path-test {
              yang-version 1.1;
              namespace urn:opendaylight:action-path-test;
              prefix "path-test";

              revision "2023-05-30" {
                description
                "Initial revision.";
              }

              rpc rpc-call {
                input {
                  leaf rpc-call-input {
                    type string;
                  }
                }
                output {
                  leaf rpc-call-output {
                    type string;
                  }
                }
              }

              container foo {
                list foo-list {
                  key "fooListKey";
                  leaf fooListKey {
                    type string;
                  }
                }
                action foo-action {
                  input {
                    leaf foo-action-input {
                      type string;
                    }
                  }
                  output {
                    leaf foo-action-output {
                      type string;
                    }
                  }
                }
              }
            }
            """);

        when(schemaService.getGlobalContext()).thenReturn(context);
        final var mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));
        final var mountPointService = mock(DOMMountPointService.class);
        when(mountPointService.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));
        final var generator = new MountPointOpenApiGeneratorRFC8040(schemaService, mountPointService, basePath);
        openApi = generator.getMountPointOpenApi();
        openApi.onMountPointCreated(INSTANCE_ID);
    }

    @Test
    public void testCustomOpenApiBasePath() throws Exception {
        final var mockInfo = DocGenTestHelper.createMockUriInfo("http://localhost/path");
        final var mountPointApi = openApi.getMountPointApi(mockInfo, 1L, "custom-base-path-test", "2023-05-30");
        assertNotNull("Failed to find Datastore API", mountPointApi);

        final var containerOperationRoot = "/" + basePath + "/operations/nodes/node=123/yang-ext:mount";
        final var containerDataRoot = "/" + basePath + "/data/nodes/node=123/yang-ext:mount";
        final var expectedPaths = Set.of(
            containerOperationRoot + "/custom-base-path-test:rpc-call",
            containerOperationRoot + "/custom-base-path-test:foo/foo-action",
            containerDataRoot + "/custom-base-path-test:foo/foo-list={fooListKey}",
            containerDataRoot + "/custom-base-path-test:foo",
            containerDataRoot);

        assertEquals("Unexpected paths", expectedPaths, mountPointApi.paths().keySet());
    }
}
