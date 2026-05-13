/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.openapi.OpenApiResourceProvider;

class ControllerDocumentOauth2Test extends AbstractOpenApiTest {
    private static final String AUTH_URL = "https://example.com/auth";
    private static final String TOKEN_URL = "https://example.com/token";
    private static final String REFRESH_URL = "https://example.com/refresh";

    @Override
    protected OpenApiResourceProvider.Configuration createOpenApiConfiguration() {
        return new OpenApiResourceProvider.Configuration() {
            @Override
            public String api$_$root$_$path() {
                return RESTS;
            }

            @Override
            public String oauth2$_$authorization$_$url() {
                return AUTH_URL;
            }

            @Override
            public String oauth2$_$token$_$url() {
                return TOKEN_URL;
            }

            @Override
            public String oauth2$_$refresh$_$url() {
                return REFRESH_URL;
            }

            @Override
            public Class<OpenApiResourceProvider.Configuration> annotationType() {
                return OpenApiResourceProvider.Configuration.class;
            }
        };
    }

    @Test
    void oauth2SecuritySchemeIsPresentWhenConfigured() throws Exception {
        final var response = invokeRequest(HttpMethod.GET, API_V3_PATH + "/single");
        assertEquals(HttpResponseStatus.OK, response.status());

        final var root = MAPPER.readTree(response.content().toString(StandardCharsets.UTF_8));

        // securitySchemes must contain both basicAuth and oauth2
        final var securitySchemes = root.path("components").path("securitySchemes");
        assertNotNull(securitySchemes.get("basicAuth"), "basicAuth scheme must be present");
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
        assertEquals(0, flow.path("scopes").size(), "scopes must be empty");

        // top-level security must list both basicAuth and oauth2
        final var security = root.path("security");
        assertTrue(security.isArray(), "security must be an array");
        boolean hasBasicAuth = false;
        boolean hasOauth2 = false;
        for (final var entry : security) {
            if (entry.has("basicAuth")) {
                hasBasicAuth = true;
            }
            if (entry.has("oauth2")) {
                hasOauth2 = true;
            }
        }
        assertTrue(hasBasicAuth, "security must include basicAuth");
        assertTrue(hasOauth2, "security must include oauth2");
    }
}
