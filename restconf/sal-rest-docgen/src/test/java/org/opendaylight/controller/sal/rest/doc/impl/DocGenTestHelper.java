/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.rest.doc.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.mockito.ArgumentCaptor;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class DocGenTestHelper {

    private Set<Module> modules;
    private ObjectMapper mapper;
    private SchemaContext schemaContext;

    public Set<Module> loadModules(final String resourceDirectory)
            throws URISyntaxException, FileNotFoundException, ReactorException {

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

        this.schemaContext = YangParserTestUtils.parseYangSources(files);
        return this.schemaContext.getModules();
    }

    public Collection<Module> getModules() {
        return this.modules;
    }

    public void setUp() throws Exception {
        this.modules = loadModules("/yang");
        this.mapper = new ObjectMapper();
        this.mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    public SchemaContext getSchemaContext() {
        return this.schemaContext;
    }

    public DOMSchemaService createMockSchemaService() {
        return createMockSchemaService(null);
    }

    public DOMSchemaService createMockSchemaService(SchemaContext mockContext) {
        if (mockContext == null) {
            mockContext = createMockSchemaContext();
        }

        final DOMSchemaService mockSchemaService = mock(DOMSchemaService.class);
        when(mockSchemaService.getGlobalContext()).thenReturn(mockContext);
        return mockSchemaService;
    }

    public SchemaContext createMockSchemaContext() {
        final SchemaContext mockContext = mock(SchemaContext.class);
        when(mockContext.getModules()).thenReturn(this.modules);

        final ArgumentCaptor<String> moduleCapture = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Date> dateCapture = ArgumentCaptor.forClass(Date.class);
        final ArgumentCaptor<URI> namespaceCapture = ArgumentCaptor.forClass(URI.class);
        when(mockContext.findModuleByName(moduleCapture.capture(), dateCapture.capture())).then(
                invocation -> {
                    final String module = moduleCapture.getValue();
                    final Date date = dateCapture.getValue();
                    for (final Module m : Collections.unmodifiableSet(DocGenTestHelper.this.modules)) {
                        if (m.getName().equals(module) && m.getRevision().equals(date)) {
                            return m;
                        }
                    }
                    return null;
                });
        when(mockContext.findModuleByNamespaceAndRevision(namespaceCapture.capture(), dateCapture.capture())).then(
                invocation -> {
                    final URI namespace = namespaceCapture.getValue();
                    final Date date = dateCapture.getValue();
                    for (final Module m : Collections.unmodifiableSet(DocGenTestHelper.this.modules)) {
                        if (m.getNamespace().equals(namespace) && m.getRevision().equals(date)) {
                            return m;
                        }
                    }
                    return null;
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
        when(info.getBaseUri()).thenReturn(uri);
        return info;
    }

}
