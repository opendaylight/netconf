/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ChunkedFramingMechanismDecoderTest {
    private static final String CHUNKED_MESSAGE = "\n#4\n"
            + "<rpc"
            + "\n#18\n"
            + " message-id=\"102\"\n"
            + "\n#79\n"
            + "     xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "  <close-session/>\n"
            + "</rpc>"
            + "\n##\n";

    private static final String EXPECTED_MESSAGE = "<rpc message-id=\"102\"\n"
            + "     xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "  <close-session/>\n"
            + "</rpc>";

    private static final String CHUNKED_MESSAGE_ONE = "\n#101\n" + EXPECTED_MESSAGE + "\n##\n";

    private final ChunkedFramingMechanismDecoder decoder = new ChunkedFramingMechanismDecoder(4096);

    @Test
    void testMultipleChunks() {
        final var output = new ArrayList<>();
        final var input = Unpooled.copiedBuffer(CHUNKED_MESSAGE.getBytes(StandardCharsets.UTF_8));
        decoder.decode(null, input, output);

        assertEquals(1, output.size());
        final var chunk = (ByteBuf) output.get(0);

        assertEquals(EXPECTED_MESSAGE, chunk.toString(StandardCharsets.UTF_8));
    }

    @Test
    void testOneChunks() {
        final var output = new ArrayList<>();
        final var input = Unpooled.copiedBuffer(CHUNKED_MESSAGE_ONE.getBytes(StandardCharsets.UTF_8));
        decoder.decode(null, input, output);

        assertEquals(1, output.size());
        final ByteBuf chunk = (ByteBuf) output.get(0);

        assertEquals(EXPECTED_MESSAGE, chunk.toString(StandardCharsets.UTF_8));
    }
}
