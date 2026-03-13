/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

public class AltSvcHttp2Test extends AbstractHttp2E2ETest {
    /**
     * Verifies that the server advertises HTTP/3 support using the Alt-Svc header.
     */
    @Test
    void altSvcPresentTest() throws Exception {
        var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri("/rests/data"))
            .GET()
            .header(HttpHeaderNames.ACCEPT.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertTrue(response.headers().map().containsKey(HttpHeaderNames.ALT_SVC.toString()));
        assertEquals("h3=\":8443\"; ma=3600",
            response.headers().firstValue(HttpHeaderNames.ALT_SVC.toString()).orElseThrow());
    }
}
