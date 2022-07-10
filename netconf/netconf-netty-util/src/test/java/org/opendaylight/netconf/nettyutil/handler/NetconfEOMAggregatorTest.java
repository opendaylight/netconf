/*
 * Copyright (c) 2022 Verizon and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static org.junit.Assert.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class NetconfEOMAggregatorTest {
    private static final String ENCODED_MESSAGE1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"105\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "<users>\n"
            + "<user><name>root</name><type>superuser</type></user>\n"
            + "</users>\n"
            + "</config>\n"
            + "</rpc-reply>\n"
            + "]]>]]>\n"
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private static final String ENCODED_MESSAGE2_PART1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"105\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n";
    private static final String ENCODED_MESSAGE2_PART2 = "<users>\n"
            + "<user><name>root</name><type>superuser</type></user>\n"
            + "</users>\n";
    private static final String ENCODED_MESSAGE2_PART3 = "</config>\n"
            + "</rpc-reply>\n"
            + "]]>]]>\n"
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private static final String EXPECTED_MESSAGE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"105\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "<users>\n"
            + "<user><name>root</name><type>superuser</type></user>\n"
            + "</users>\n"
            + "</config>\n"
            + "</rpc-reply>\n";

    private static NetconfEOMAggregator agr;

    @Before
    public void setUp() throws Exception {
        agr = new NetconfEOMAggregator();
    }

    @Test
    public void testDecodeSingle() throws Exception {
        final List<Object> output = new ArrayList<>();
        final ByteBuf input = Unpooled.buffer(1000);

        input.writeBytes(ENCODED_MESSAGE1.getBytes(StandardCharsets.UTF_8));
        agr.decode(null, input, output);
        assertEquals(1, output.size());
        final ByteBuf frame = (ByteBuf) output.get(0);
        assertEquals(EXPECTED_MESSAGE, frame.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void testDecodeMultiple() throws Exception {
        final List<Object> output = new ArrayList<>();
        final ByteBuf input = Unpooled.buffer(1000);

        input.writeBytes(ENCODED_MESSAGE2_PART1.getBytes(StandardCharsets.UTF_8));
        agr.decode(null, input, output);
        assertEquals(0, output.size());

        input.writeBytes(ENCODED_MESSAGE2_PART2.getBytes(StandardCharsets.UTF_8));
        agr.decode(null, input, output);
        assertEquals(0, output.size());

        input.writeBytes(ENCODED_MESSAGE2_PART3.getBytes(StandardCharsets.UTF_8));
        agr.decode(null, input, output);
        assertEquals(1, output.size());
        final ByteBuf frame = (ByteBuf) output.get(0);
        assertEquals(EXPECTED_MESSAGE, frame.toString(StandardCharsets.UTF_8));
    }
}
