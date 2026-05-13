/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for OpenAPI documentation generated when OAuth2/OIDC authorization is configured. Verifies that
 * the {@code securitySchemes} object and the top-level {@code security} array are populated correctly alongside the
 * always-present {@code basicAuth} scheme.
 */
class ControllerDocumentOauth2Test extends AbstractOpenApiTest {
    private static final String AUTH_URL = "https://example.com/auth";
    private static final String TOKEN_URL = "https://example.com/token";
    private static final String REFRESH_URL = "https://example.com/refresh";

    private JsonNode root;

    @BeforeEach
    void setUp() throws Exception{
        final var response = invokeRequest(HttpMethod.GET, API_V3_PATH + "/single");
        assertEquals(HttpResponseStatus.OK, response.status());
        root = MAPPER.readTree(response.content().toString(StandardCharsets.UTF_8));
    }

    /**
     * Tests that OAuth2 authorization is available.
     *
     * <p>When OAuth2 configuration is available the user should be able to use authorize button to log in to IdP.
     * That is verified by checking {@code securitySchemes/oauth2}.
     *
     * <p>When OAuth2 configuration is available ALL requests should be marked in OpenAPI documentation as secured by
     * Oauth2. That is verified by checking {@code root/security/oauth2}.
     *
     * <p>All ODL's APIs are by default supporting basic auth thus it is being present even when we configure OAuth2.
     */
    @Test
    void oauth2SecuritySchemeIsPresentWhenConfigured() {
        // securitySchemes must contain both basicAuth and oauth2
        final var securitySchemes = root.path("components").path("securitySchemes");
        assertNotNull(securitySchemes.get("basicAuth"), "basicAuth scheme must be present");
        assertNotNull(securitySchemes.get("oauth2"), "oauth2 scheme must be present when OAuth2 URLs are configured");

        // top-level security must list both basicAuth and oauth2
        final var security = root.path("security");
        assertTrue(security.isArray(), "security must be an array");
        assertTrue(security.has("basicAuth"), "security must include basicAuth");
        assertTrue(security.has("oauth2"), "security must include oauth2");
    }

    /**
     * Tests the structure of the OAuth2 security scheme when configured.
     *
     * <p>Verifies that the {@code oauth2} entry inside {@code components/securitySchemes} uses type {@code oauth2} and
     * exposes an {@code authorizationCode} flow whose {@code authorizationUrl}, {@code tokenUrl}, and
     * {@code refreshUrl} match the configured values. Also checks that {@code scopes} is present as an empty object,
     * which is required by the OpenAPI 3.0 specification even when no explicit scopes are defined.
     */
    @Test
    void oauth2SecuritySchemeConfigurationTest() {
        final var securitySchemes = root.path("components").path("securitySchemes");
        final var oauth2Scheme = securitySchemes.get("oauth2");
        assertNotNull(oauth2Scheme, "oauth2 scheme must be present when OAuth2 URLs are configured");

        // oauth2 scheme structure
        assertEquals("oauth2", oauth2Scheme.path("type").asText());
        final var flow = oauth2Scheme.path("flows").path("authorizationCode");
        assertNotNull(flow, "authorizationCode flow must be present");
        assertEquals(AUTH_URL, flow.path("authorizationUrl").asText());
        assertEquals(TOKEN_URL, flow.path("tokenUrl").asText());
        assertEquals(REFRESH_URL, flow.path("refreshUrl").asText());
        assertTrue(flow.path("scopes").isObject(), "scopes must be an object");
        assertTrue(flow.path("scopes").isEmpty(), "scopes must be empty");
    }
}
