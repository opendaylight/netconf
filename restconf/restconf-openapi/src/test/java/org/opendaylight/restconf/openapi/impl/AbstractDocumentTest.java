/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.internal.util.collection.ImmutableMultivaluedMap;
import org.mockito.ArgumentCaptor;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONCompareMode;

public abstract class AbstractDocumentTest {
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    /**
     * We want flexibility in comparing the resulting JSONs by not enforcing strict ordering of array contents.
     * This comparison mode allows us to do that and also to restrict extensibility (extensibility = additional fields)
     */
    protected static final JSONCompareMode IGNORE_ORDER = JSONCompareMode.NON_EXTENSIBLE;
    private static final String URI = "http://localhost:8181/openapi/api/v3/";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
        .node(QName.create("", "nodes"))
        .node(QName.create("", "node"))
        .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private static OpenApiService openApiService;

    protected static void initializeClass(final String yangPath) {
        final var schemaService = mock(DOMSchemaService.class);
        final var context = YangParserTestUtils.parseYangResourceDirectory(yangPath);
        when(schemaService.getGlobalContext()).thenReturn(context);

        final var mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final var service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));

        final var mountPointRFC8040 = new MountPointOpenApiGeneratorRFC8040(schemaService, service);
        final var openApiGeneratorRFC8040 = new OpenApiGeneratorRFC8040(schemaService);
        mountPointRFC8040.getMountPointOpenApi().onMountPointCreated(INSTANCE_ID);
        openApiService = new OpenApiServiceImpl(mountPointRFC8040, openApiGeneratorRFC8040);
    }

    protected static String getExpectedDoc(final String jsonPath) throws Exception {
        return MAPPER.writeValueAsString(MAPPER.readTree(
            AbstractDocumentTest.class.getClassLoader().getResourceAsStream(jsonPath)));
    }

    protected static String getAllModulesDoc() throws Exception {
        final var getAllController = createMockUriInfo(URI + "single");
        final var controllerDocAll = openApiService.getAllModulesDoc(getAllController).getEntity();

        return new String(((OpenApiInputStream) controllerDocAll).readAllBytes(),
            StandardCharsets.UTF_8);
    }

    protected static String getDocByModule(final String moduleName, final String revision) throws Exception {
        var uri = URI + moduleName;
        if (revision != null) {
            uri = uri + "(" + revision + ")";
        }
        final var getModuleController = createMockUriInfo(uri);
        final var controllerDocModule = openApiService.getDocByModule(moduleName, revision, getModuleController);

        return new String(((OpenApiInputStream) controllerDocModule.getEntity()).readAllBytes(),
            StandardCharsets.UTF_8);
    }


    protected static String getMountDoc() throws Exception {
        final var getAllDevice = createMockUriInfo(URI + "mounts/1");
        when(getAllDevice.getQueryParameters()).thenReturn(ImmutableMultivaluedMap.empty());
        final var deviceDocAll = openApiService.getMountDoc("1", getAllDevice);

        return new String(((OpenApiInputStream) deviceDocAll.getEntity()).readAllBytes(),
            StandardCharsets.UTF_8);
    }


    protected static String getMountDocByModule(final String moduleName, final String revision) throws Exception {
        final var getDevice = createMockUriInfo(URI + "mounts/1/" + moduleName);
        final var deviceDoc = openApiService.getMountDocByModule("1", moduleName, revision, getDevice);

        return new String(((OpenApiInputStream) deviceDoc.getEntity()).readAllBytes(),
            StandardCharsets.UTF_8);
    }

    public static UriInfo createMockUriInfo(final String urlPrefix) throws Exception {
        final java.net.URI uri = new URI(urlPrefix);
        final UriBuilder mockBuilder = mock(UriBuilder.class);

        final ArgumentCaptor<String> subStringCapture = ArgumentCaptor.forClass(String.class);
        when(mockBuilder.path(subStringCapture.capture())).thenReturn(mockBuilder);
        when(mockBuilder.build()).then(invocation -> java.net.URI.create(uri + "/" + subStringCapture.getValue()));

        final UriInfo info = mock(UriInfo.class);
        when(info.getRequestUriBuilder()).thenReturn(mockBuilder);
        when(mockBuilder.replaceQuery(any())).thenReturn(mockBuilder);
        when(info.getBaseUri()).thenReturn(uri);

        return info;
    }
}
