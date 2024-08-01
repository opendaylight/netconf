/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.opendaylight.restconf.server.PathParameters.DATA;
import static org.opendaylight.restconf.server.ProcessorTestUtils.buildRequest;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

class ResponseUtilsTest {
    private static final String RESTS = "rests";
    private static final String DATA_PATH = "/" + RESTS + DATA + "/";

    @Test
    void handleException() {
        FullHttpRequest request = buildRequest(HttpMethod.GET, DATA_PATH, ProcessorTestUtils.TestEncoding.JSON, null);
    }
}
