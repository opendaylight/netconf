/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.openapi.model.DocumentEntity;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONCompareMode;

abstract class AbstractDocumentTest {
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

    private static final JsonFactory JSON_FACTORY = new JsonFactoryBuilder().build();

    private static OpenApiService openApiService;

    protected static void initializeClass(final String yangPath) {
        final var schemaService = mock(DOMSchemaService.class);
        final var context = YangParserTestUtils.parseYangResourceDirectory(yangPath);
        when(schemaService.getGlobalContext()).thenReturn(context);

        final var mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getIdentifier()).thenReturn(INSTANCE_ID);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final var service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));

        final var mountPointRFC8040 = new MountPointOpenApiGeneratorRFC8040(schemaService, service, "rests");
        final var openApiGeneratorRFC8040 = new OpenApiGeneratorRFC8040(schemaService, "rests");
        mountPointRFC8040.getMountPointOpenApi().onMountPointCreated(mountPoint);
        openApiService = new OpenApiServiceImpl(mountPointRFC8040, openApiGeneratorRFC8040);
    }

    protected static String getExpectedDoc(final String jsonPath) throws Exception {
        return MAPPER.writeValueAsString(MAPPER.readTree(
            AbstractDocumentTest.class.getClassLoader().getResourceAsStream(jsonPath)));
    }

    protected static String getAllModulesDoc(final int width, final int depth, final int offset, final int limit)
            throws Exception {
        return assertOpenApiEntity(openApiService.getAllModulesDoc(new URI(URI + "single"), width, depth, offset,
            limit));
    }

    protected static String getDocByModule(final String moduleName, final String revision) throws Exception {
        var uri = URI + moduleName;
        if (revision != null) {
            uri = uri + "(" + revision + ")";
        }
        return assertOpenApiEntity(openApiService.getDocByModule(moduleName, revision, new URI(uri), 0, 0));
    }

    protected static String getMountDoc(final int width, final int depth, final int offset, final int limit)
            throws Exception {
        return assertOpenApiEntity(openApiService.getMountDoc(1, new URI(URI + "mounts/1"), width, depth, offset,
            limit));
    }

    protected static String getMountDocByModule(final String moduleName, final String revision) throws Exception {
        return assertOpenApiEntity(openApiService.getMountDocByModule(1, moduleName, revision,
            new URI(URI + "mounts/1/" + moduleName), 0, 0));
    }

    private static String assertOpenApiEntity(final DocumentEntity entity) {
        final var sw = new StringWriter();
        try (var generator = JSON_FACTORY.createGenerator(sw)) {
            entity.generate(generator);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return sw.toString();
    }
}
