/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class ToasterDocumentTest extends AbstractDocumentTest {
    private static final String TOASTER = "toaster";
    private static final String TOASTER_REV = "2009-11-20";

    @BeforeAll
    public static void beforeClass() {
        initializeClass("/toaster-document/");
    }

    /**
     * Tests the swagger document that is result of the call to the '/single' endpoint.
     */
    @Test
    public void getAllModulesDocTest() throws Exception {
        final var jsonControllerDoc = getAllModulesDoc();
        final var expectedJson = getExpectedDoc("toaster-document/controller-all.json");
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/toaster(2009-11-20)' endpoint.
     */
    @Test
    public void getDocByModuleTest() throws Exception {
        final var jsonControllerDoc = getDocByModule(TOASTER, TOASTER_REV);
        final var expectedJson = getExpectedDoc("toaster-document/controller-toaster.json");
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1' endpoint.
     */
    @Test
    public void getMountDocTest() throws Exception {
        final var jsonDeviceDoc = getMountDoc();
        final var expectedJson = getExpectedDoc("toaster-document/device-all.json");
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/toaster(2009-11-20)' endpoint.
     */
    @Test
    public void getMountDocByModuleTest() throws Exception {
        final var jsonDeviceDoc = getMountDocByModule(TOASTER, TOASTER_REV);
        final var expectedJson = getExpectedDoc("toaster-document/device-toaster.json");
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }
}
