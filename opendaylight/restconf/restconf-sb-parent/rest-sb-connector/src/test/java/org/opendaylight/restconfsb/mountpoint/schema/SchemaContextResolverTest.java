/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.restconfsb.communicator.impl.xml.draft04.RestconfXmlParser;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class SchemaContextResolverTest {

    private InputStream inputStream;
    private List<Module> modules;

    @Before
    public void initializeVariables() {
        inputStream = getClass().getResourceAsStream("/xml/modules_for_test.xml");
        modules = new RestconfXmlParser().parseModules(inputStream);
    }

    @Test
    public void testCreateSchemaContext() throws Exception {
        String cacheSchema = getClass().getResource("/cache/schema/all/").getPath();
        DirectorySchemaContextCache directorySchemaContextCache = new DirectorySchemaContextCache(cacheSchema);
        SchemaContextResolver testSchemaContextResolver = new SchemaContextResolver(directorySchemaContextCache);
        SchemaContext schemaContext = testSchemaContextResolver.createSchemaContext(modules);

        assertEquals("Schema context modules size and required modules size are not equal.", modules.size(), schemaContext.getModules().size());
        List<String> moduleNames = new ArrayList<>();
        for (org.opendaylight.yangtools.yang.model.api.Module module : schemaContext.getModules()) {
            moduleNames.add(module.getName());
        }
        for (Module module : modules) {
            assertTrue("Some module defined in modules.xml is absent in SchemaContext.", moduleNames.contains(module.getName().getValue()));
        }
    }

    @Test
    public void testCreateSchemaContextMissingModules() throws Exception {
        String cacheSchema = getClass().getResource("/cache/schema/missing/").getPath();
        DirectorySchemaContextCache directorySchemaContextCache = new DirectorySchemaContextCache(cacheSchema);
        SchemaContextResolver testSchemaContextResolver = new SchemaContextResolver(directorySchemaContextCache);
        SchemaContext schemaContext = testSchemaContextResolver.createSchemaContext(modules);
        assertTrue("Failed to create SchemaSource from biggest possible subset of available sources.", schemaContext.getModules().size() > 0);
    }

}