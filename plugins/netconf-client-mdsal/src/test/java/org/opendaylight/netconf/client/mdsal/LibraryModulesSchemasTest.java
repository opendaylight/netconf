/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.junit.Assert.assertEquals;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import org.junit.Test;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

public class LibraryModulesSchemasTest {

    @Test
    public void testCreate() throws Exception {
        // test create from xml
        LibraryModulesSchemas libraryModulesSchemas =
                LibraryModulesSchemas.create(getClass().getResource("/yang-library.xml").toString());

        verifySchemas(libraryModulesSchemas);

        // test create from json
        LibraryModulesSchemas libraryModuleSchemas =
                LibraryModulesSchemas.create(getClass().getResource("/yang-library.json").toString());

        verifySchemas(libraryModuleSchemas);
    }

    private static void verifySchemas(final LibraryModulesSchemas libraryModulesSchemas) throws MalformedURLException {
        assertEquals(Map.of(
            new SourceIdentifier("module-with-revision", "2014-04-08"),
            new URL("http://localhost:8181/yanglib/schemas/module-with-revision/2014-04-08"),
            new SourceIdentifier("another-module-with-revision", "2013-10-21"),
            new URL("http://localhost:8181/yanglib/schemas/another-module-with-revision/2013-10-21"),
            new SourceIdentifier("module-without-revision"),
            new URL("http://localhost:8181/yanglib/schemas/module-without-revision/")),

            libraryModulesSchemas.getAvailableModels());
    }

    @Test
    public void testCreateInvalidModulesEntries() throws Exception {
        LibraryModulesSchemas libraryModulesSchemas =
                LibraryModulesSchemas.create(getClass().getResource("/yang-library-fail.xml").toString());

        assertEquals(Map.of(new SourceIdentifier("good-ol-module"), new URL("http://www.example.com")),
            libraryModulesSchemas.getAvailableModels());
    }

    @Test
    public void testCreateFromInvalidAll() throws Exception {
        // test bad yang lib url
        LibraryModulesSchemas libraryModulesSchemas = LibraryModulesSchemas.create("ObviouslyBadUrl");
        assertEquals(Map.of(), libraryModulesSchemas.getAvailableModels());

        // TODO test also fail on json and xml parsing. But can we fail not on runtime exceptions?
    }
}