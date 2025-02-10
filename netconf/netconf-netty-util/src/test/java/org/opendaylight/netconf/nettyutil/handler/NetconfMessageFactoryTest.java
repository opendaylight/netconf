/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class NetconfMessageFactoryTest {
    @Test
    void testAuth() throws Exception {
        final var parser = new HelloXMLMessageDecoder();
        final var authHelloFile =
            Path.of(getClass().getResource("/netconfMessages/client_hello_with_auth.xml").getFile());

        final var out = new ArrayList<>();
        parser.decode(null, Unpooled.wrappedBuffer(Files.readAllBytes(authHelloFile)), out);
        assertEquals(1, out.size());
    }
}
