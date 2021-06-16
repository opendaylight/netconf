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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.mockito.ArgumentCaptor;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class DocGenTestHelper {

    private Collection<? extends Module> modules;
    private ObjectMapper mapper;
    private EffectiveModelContext schemaContext;

    public Collection<? extends Module> loadModules(final String resourceDirectory)
            throws URISyntaxException, FileNotFoundException {

        final URI resourceDirUri = getClass().getResource(resourceDirectory).toURI();
        final File testDir = new File(resourceDirUri);
        final String[] fileList = testDir.list();
        if (fileList == null) {
            throw new FileNotFoundException(resourceDirectory.toString());
        }
        final List<File> files = new ArrayList<>();
        for (final String fileName : fileList) {
            files.add(new File(testDir, fileName));
        }

        this.schemaContext = YangParserTestUtils.parseYangFiles(files);
        return this.schemaContext.getModules();
    }

    public Collection<? extends Module> getModules() {
        return this.modules;
    }

    public void setUp() throws Exception {
        this.modules = loadModules("/yang");
        this.mapper = new ObjectMapper();
        this.mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    public EffectiveModelContext getSchemaContext() {
        return this.schemaContext;
    }

    public DOMSchemaService createMockSchemaService(EffectiveModelContext mockContext) {
        if (mockContext == null) {
            mockContext = createMockSchemaContext();
        }

        final DOMSchemaService mockSchemaService = mock(DOMSchemaService.class);
        when(mockSchemaService.getGlobalContext()).thenReturn(mockContext);
        return mockSchemaService;
    }

    public EffectiveModelContext createMockSchemaContext() {
        final EffectiveModelContext mockContext = mock(EffectiveModelContext.class);
        doReturn(this.modules).when(mockContext).getModules();

        final ArgumentCaptor<String> moduleCapture = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Optional> dateCapture = ArgumentCaptor.forClass(Optional.class);
        final ArgumentCaptor<XMLNamespace> namespaceCapture = ArgumentCaptor.forClass(XMLNamespace.class);
        when(mockContext.findModule(moduleCapture.capture(), dateCapture.capture())).then(
            invocation -> {
                final String module = moduleCapture.getValue();
                final Optional<?> date = dateCapture.getValue();
                for (final Module m : DocGenTestHelper.this.modules) {
                    if (m.getName().equals(module) && m.getRevision().equals(date)) {
                        return Optional.of(m);
                    }
                }
                return Optional.empty();
            });
        when(mockContext.findModule(namespaceCapture.capture(), dateCapture.capture())).then(
            invocation -> {
                final XMLNamespace namespace = namespaceCapture.getValue();
                final Optional<?> date = dateCapture.getValue();
                for (final Module m : DocGenTestHelper.this.modules) {
                    if (m.getNamespace().equals(namespace) && m.getRevision().equals(date)) {
                        return Optional.of(m);
                    }
                }
                return Optional.empty();
            });
        return mockContext;
    }

    public UriInfo createMockUriInfo(final String urlPrefix) throws URISyntaxException {
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

    static Module findModule(final String namespace, final String revisionDate,
            final Collection<? extends Module> modules) {
        assertFalse("No modules found", modules == null || modules.isEmpty());
        final Optional<? extends Module> module = modules.stream()
                .filter(modulesFilter(namespace, revisionDate))
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
