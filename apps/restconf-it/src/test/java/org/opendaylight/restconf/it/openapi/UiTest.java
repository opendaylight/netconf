/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi;

import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UiTest extends AbstractOpenApiTest {
    @Test
    void uiAvailableTest() throws Exception {
        var response = invokeRequest(HttpMethod.GET, API_V3_PATH + "/ui", null, TEXT_HTML, null);
        assertEquals(HttpResponseStatus.SEE_OTHER, response.status());
        final var location = response.headers().get("location");
        response = invokeRequest(HttpMethod.GET, location, null, TEXT_HTML, null);
        assertEquals("""
            <!-- HTML for static distribution bundle build -->
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="UTF-8">
                <title>RestConf Documentation</title>
                <link rel="stylesheet" type="text/css" href="swagger-ui/swagger-ui.css" />
                <link rel="stylesheet" type="text/css" href="swagger-ui/index.css" />
                <link rel="stylesheet" type="text/css" href="styles.css" />
                <link rel="icon" type="image/png" href="swagger-ui/favicon-32x32.png" sizes="32x32" />
                <link rel="icon" type="image/png" href="swagger-ui/favicon-16x16.png" sizes="16x16" />
              </head>

              <body>
                <div id="swagger-ui"></div>
                <script src="swagger-ui/swagger-ui-bundle.js" charset="UTF-8"> </script>
                <script src="swagger-ui/swagger-ui-standalone-preset.js" charset="UTF-8"> </script>
                <script src="./configuration.js"></script>
                <script src="./swagger-initializer.js" charset="UTF-8"> </script>
              </body>
            </html>""".trim(), response.content().toString(StandardCharsets.UTF_8).trim());
    }
}
