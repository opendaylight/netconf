/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;

public class AltSvcTest extends AbstractE2ETest {
    /**
     * Verifies that the server advertises HTTP/3 support using the Alt-Svc header.
     */
    @Test
    void altSvcPresentTest() throws Exception {
        final var response = invokeRequest(HttpMethod.GET, "/rests/data");

        assertEquals(HttpResponseStatus.OK, response.status());
        assertTrue(response.headers().contains(HttpHeaderNames.ALT_SVC));
        assertEquals("h3=\":8443\"; ma=3600", response.headers().get(HttpHeaderNames.ALT_SVC));
    }
}
