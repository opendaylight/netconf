/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opendaylight.yangtools.yang.common.YangConstants.RFC6020_YANG_MEDIA_TYPE;
import static org.opendaylight.yangtools.yang.common.YangConstants.RFC6020_YIN_MEDIA_TYPE;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

class ModulesE2ETest extends AbstractE2ETest {

    @Test
    void modulesOptions() throws Exception {
        final var result = invokeRequest(HttpMethod.OPTIONS, "/rests/modules");
        assertEquals(200, result.status().code());
        // todo validate headers
    }

    @Test
    void readModulesList() throws Exception {
        final var result = invokeRequest(HttpMethod.GET, "/rests/modules");
        assertEquals(200, result.status().code());
        // TODO validate content
    }

    @Test
    void readYin() throws Exception {
        final var result = invokeRequest(HttpMethod.GET,
            "/rests/modules/network-topology?revision=2013-10-21",
            RFC6020_YIN_MEDIA_TYPE);
        assertEquals(200, result.status().code());
    }

    @Test
    void readYang() throws Exception {
        final var result = invokeRequest(HttpMethod.GET,
            "/rests/modules/network-topology?revision=2013-10-21",
            RFC6020_YANG_MEDIA_TYPE);
        assertEquals(200, result.status().code());
    }
}
