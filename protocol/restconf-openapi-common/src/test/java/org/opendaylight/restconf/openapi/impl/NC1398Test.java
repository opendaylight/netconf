/*
 * Copyright (c) 2024 Verizon and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class NC1398Test extends AbstractDocumentTest {
    @BeforeAll
    static void beforeAll() {
        initializeClass("/nc1398/yang/");
    }

    /**
     * Tests the swagger document generated for the regex@2024-09-25.yang model.
     */
    @Test
    void getDocByModuleTest() throws Exception {
        final var expectedJson = getExpectedDoc("nc1398/document/regex.json");
        final var moduleDoc = getDocByModule("regex", "2024-09-25");
        JSONAssert.assertEquals(expectedJson, moduleDoc, IGNORE_ORDER);
    }
}
