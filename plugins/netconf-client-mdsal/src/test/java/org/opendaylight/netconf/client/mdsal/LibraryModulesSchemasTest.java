/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;

class LibraryModulesSchemasTest {

    @Test
    void testCreate() throws Exception {
        // test create from xml
        LibraryModulesSchemas libraryModulesSchemas =
                LibraryModulesSchemas.create(getClass().getResource("/yang-library.xml").toString());

        verifySchemas(libraryModulesSchemas);

        // test create from json
        LibraryModulesSchemas libraryModuleSchemas =
                LibraryModulesSchemas.create(getClass().getResource("/yang-library.json").toString());

        verifySchemas(libraryModuleSchemas);
    }

    private static void verifySchemas(final LibraryModulesSchemas libraryModulesSchemas) throws Exception {
        assertEquals(Map.of(
            new SourceIdentifier("module-with-revision", "2014-04-08"),
            new URI("http://localhost:8181/yanglib/schemas/module-with-revision/2014-04-08").toURL(),
            new SourceIdentifier("another-module-with-revision", "2013-10-21"),
            new URI("http://localhost:8181/yanglib/schemas/another-module-with-revision/2013-10-21").toURL(),
            new SourceIdentifier("module-without-revision"),
            new URI("http://localhost:8181/yanglib/schemas/module-without-revision/").toURL()),

            libraryModulesSchemas.getAvailableModels());
    }

    @Test
    void testCreateInvalidModulesEntries() throws Exception {
        LibraryModulesSchemas libraryModulesSchemas =
                LibraryModulesSchemas.create(getClass().getResource("/yang-library-fail.xml").toString());

        assertEquals(Map.of(new SourceIdentifier("good-ol-module"), new URI("http://www.example.com").toURL()),
            libraryModulesSchemas.getAvailableModels());
    }

    @Test
    void testCreateFromInvalidAll() {
        // test bad yang lib url
        LibraryModulesSchemas libraryModulesSchemas = LibraryModulesSchemas.create("ObviouslyBadUrl");
        assertEquals(Map.of(), libraryModulesSchemas.getAvailableModels());

        // TODO test also fail on json and xml parsing. But can we fail not on runtime exceptions?
    }
}
