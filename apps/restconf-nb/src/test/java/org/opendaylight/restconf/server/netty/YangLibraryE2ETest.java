/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

class YangLibraryE2ETest extends AbstractE2ETest {

    @Test
    void readYangLibraryVersionTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.GET,
            "rests/yang-library-version",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());
    }
}
