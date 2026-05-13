/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.openapi.model.security.OpenApiOauth2Configuration;
import org.skyscreamer.jsonassert.JSONAssert;

class Oauth2DocumentTest extends AbstractDocumentTest {
    private static final String AUTH_URL    = "https://example.com/auth";
    private static final String TOKEN_URL   = "https://example.com/token";
    private static final String REFRESH_URL = "https://example.com/refresh";

    @BeforeAll
    static void beforeClass() {
        initializeClass("/toaster-document/", new OpenApiOauth2Configuration(AUTH_URL, TOKEN_URL, REFRESH_URL));
    }

    /**
     * Tests that the OpenAPI document generated when OAuth2/OIDC is configured contains both the {@code basicAuth}
     * and {@code oauth2} security schemes and lists both in the top-level {@code security} array.
     */
    @Test
    void getAllModulesDocTest() throws Exception {
        final var actual = getAllModulesDoc(0, 0, 0, 0);
        final var expected = getExpectedDoc("toaster-document/controller-all-oauth2.json");
        JSONAssert.assertEquals(expected, actual, IGNORE_ORDER);
    }
}
