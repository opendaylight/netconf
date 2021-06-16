/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.rest.doc.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.mockito.ArgumentCaptor;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

final class DocGenTestHelper {

    private DocGenTestHelper() {
        // hidden on purpose
    }

    static DOMSchemaService createMockSchemaService(final EffectiveModelContext mockContext) {
        final DOMSchemaService mockSchemaService = mock(DOMSchemaService.class);
        when(mockSchemaService.getGlobalContext()).thenReturn(mockContext);
        return mockSchemaService;
    }

    static UriInfo createMockUriInfo(final String urlPrefix) throws Exception {
        final URI uri = new URI(urlPrefix);
        final UriBuilder mockBuilder = mock(UriBuilder.class);

        final ArgumentCaptor<String> subStringCapture = ArgumentCaptor.forClass(String.class);
        when(mockBuilder.path(subStringCapture.capture())).thenReturn(mockBuilder);
        when(mockBuilder.build()).then(invocation -> URI.create(uri + "/" + subStringCapture.getValue()));

        final UriInfo info = mock(UriInfo.class);
        when(info.getRequestUriBuilder()).thenReturn(mockBuilder);
        when(mockBuilder.replaceQuery(any())).thenReturn(mockBuilder);
        when(info.getBaseUri()).thenReturn(uri);

        return info;
    }

    /**
     * Find module according to its namespace and revision.
     *
     * <p>
     * If there are no available modules or desired module is not found
     * then assertion errors are thrown.
     *
     * @param namespace Desired module namespace
     * @param revisionDate Desired module revision
     * @param modules All available modules
     * @return Desired module
     */
    static Module findModule(final String namespace, final String revisionDate,
            final Collection<? extends Module> modules) {
        assertFalse("No modules found", modules == null || modules.isEmpty());
        final Optional<? extends Module> module = modules.stream()
                .filter(modulesFilter(namespace, revisionDate))
                .findAny();
        assertTrue("Desired module not found", module.isPresent());
        return module.get();
    }

    /**
     * Find module according to its namespace.
     *
     * <p>
     * If there are no available modules or desired module is not found
     * then assertion errors are thrown.
     *
     * @param namespace Desired module namespace
     * @param modules All available modules
     * @return Desired module
     */
    static Module findModule(final String namespace, final Collection<? extends Module> modules) {
        assertFalse("No modules found", modules == null || modules.isEmpty());
        final Optional<? extends Module> module = modules.stream()
                .filter(m -> namespace.equals(m.getQNameModule().getNamespace().toString()))
                .findAny();
        assertTrue("Desired module not found", module.isPresent());
        return module.get();
    }

    private static Predicate<Module> modulesFilter(final String namespace, final String revision) {
        return m -> namespace.equals(m.getQNameModule().getNamespace().toString())
                && m.getQNameModule().getRevision().isPresent()
                && revision.equals(m.getQNameModule().getRevision().get().toString());
    }

    /**
     * Checks whether object {@code mainObject} contains in properties/items key $ref with concrete value.
     */
    static void containsReferences(final JsonNode mainObject, final String childObject, final String expectedRef) {
        final JsonNode properties = mainObject.get("properties");
        assertNotNull(properties);

        final JsonNode childNode = properties.get(childObject);
        assertNotNull(childNode);

        //list case
        JsonNode refWrapper = childNode.get("items");
        if (refWrapper == null) {
            //container case
            refWrapper = childNode;
        }
        assertEquals(expectedRef, refWrapper.get("$ref").asText());
    }
}
