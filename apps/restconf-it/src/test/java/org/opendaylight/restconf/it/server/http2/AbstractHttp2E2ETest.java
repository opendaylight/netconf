/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.restconf.it.server.AbstractE2ETest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class AbstractHttp2E2ETest extends AbstractE2ETest {
    protected HttpClient http2Client;

    @BeforeEach
    @Override
    protected void beforeEach() throws Exception {
        super.beforeEach();
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

    @AfterEach
    @Override
    protected void afterEach() throws Exception {
        super.afterEach();
        http2Client.close();
    }

    @Override
    protected void assertContentJson(final String getRequestUri, final String expectedContent) throws Exception {
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(new URI("http://" + host + getRequestUri))
            .GET()
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertContentJson(response, expectedContent);
    }

    protected static void assertContentJson(final HttpResponse<String> response, final String expectedContent) {
        final var content = response.body();
        JSONAssert.assertEquals(expectedContent, content, JSONCompareMode.LENIENT);
    }

    protected static void assertErrorResponseJson(final HttpResponse<String> response, final ErrorType expectedErrorType,
        final ErrorTag expectedErrorTag) {
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.statusCode());
        // {
        //    errors": {
        //       "error": [{
        //             "error-type": "...",
        //             "error-tag": "..."
        //             "error-message": "..."
        //         }]
        //    }
        // }
        final var json = new JSONObject(response.body(), JSON_PARSER_CONFIGURATION);
        final var error = json.getJSONObject("errors").getJSONArray("error").getJSONObject(0);
        assertNotNull(error);
        assertEquals(expectedErrorType.elementBody(), error.getString("error-type"));
        assertEquals(expectedErrorTag.elementBody(), error.getString("error-tag"));
        assertNotNull(error.getString("error-message"));
    }
}
