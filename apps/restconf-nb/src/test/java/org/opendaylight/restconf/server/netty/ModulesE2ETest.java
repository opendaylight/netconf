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
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YIN_XML;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

class ModulesE2ETest extends AbstractE2ETest {

    @Test
    void readModulesStateTestYin() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.GET,
            "rests/modules",
            APPLICATION_YIN_XML.toString(),
            null));
        assertEquals(200, result.status().code());
    }

    @Test
    void readOneModuleStateTestYin() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.GET,
            "rests/modules/network-topology?revision=2013-10-21",
            APPLICATION_YIN_XML.toString(),
            null));
        assertEquals(200, result.status().code());
    }

    @Test
    void readModulesStateTestYan() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.GET,
            "rests/modules",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());
    }

    @Test
    void readOneModuleStateTestYan() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.GET,
            "rests/modules/network-topology?revision=2013-10-21",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());
    }
}
