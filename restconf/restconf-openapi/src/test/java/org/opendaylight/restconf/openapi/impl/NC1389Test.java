/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Test for <a href="https://lf-opendaylight.atlassian.net/browse/NETCONF-1389">NETCONF-1389</a>.
 */
class NC1389Test extends AbstractDocumentTest {

    @BeforeAll
    static void beforeAll() {
        initializeClass("/nc1389/yang/");
    }

    /**
     * Tests the swagger document generated for the leafref-test@2024-09-11.yang model.
     */
    @Test
    void getDocByModuleTest() throws Exception {
        final var expectedJson = getExpectedDoc("nc1389/document/leafref-test.json");
        final var moduleDoc = getDocByModule("leafref-test", "2024-09-11");
        JSONAssert.assertEquals(expectedJson, moduleDoc, IGNORE_ORDER);
    }
}
