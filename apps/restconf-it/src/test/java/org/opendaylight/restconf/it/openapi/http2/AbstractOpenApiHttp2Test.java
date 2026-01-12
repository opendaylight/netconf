/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi.http2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.opendaylight.restconf.it.openapi.AbstractOpenApiTest;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractOpenApiHttp2Test extends AbstractOpenApiTest {
    protected HttpClient http2Client;

    @BeforeAll
    void perClass() {
        http2Client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                        USERNAME,
                        PASSWORD.toCharArray());
                }
            })
            .build();
    }

    @AfterAll
    protected void afterClass() {
        http2Client.close();
    }

    @Override
    protected void assertContentJson(final String getRequestUri, final String expectedContent) throws Exception {
        final var response = http2Client.send(HttpRequest.newBuilder()
            .GET()
            .uri(new URI("http://" + host + getRequestUri))
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());

        final var content = response.body();
        JSONAssert.assertEquals(expectedContent, content, JSONCompareMode.LENIENT);
    }

    protected URI createApiUri(final String path) throws URISyntaxException {
        return new URI("http://" + host + API_V3_PATH + path);
    }
}
