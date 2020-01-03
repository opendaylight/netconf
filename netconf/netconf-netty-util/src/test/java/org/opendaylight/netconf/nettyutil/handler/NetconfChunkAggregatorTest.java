/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static org.junit.Assert.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.api.NetconfChunkException;

public class NetconfChunkAggregatorTest {

    private static final String CHUNKED_MESSAGE = "\n#4\n"
            + "<rpc"
            + "\n#18\n"
            + " message-id=\"102\"\n"
            + "\n#79\n"
            + "     xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "  <close-session/>\n"
            + "</rpc>"
            + "\n##\n";

    public static final String EXPECTED_MESSAGE_102 = "<rpc message-id=\"102\"\n"
            + "     xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "  <close-session/>\n"
            + "</rpc>";

    private static final String CHUNKED_MESSAGE_ONE = "\n#101\n" + EXPECTED_MESSAGE_102 + "\n##\n";

    private static final String MALFORMED_MESSAGE_WITH_TRAILING = "\n#4\n"
            + "<rpc"
            + "\n#18\n"
            + " message-id=\"102\"\n"
            + "<rpc-error><error-type>transport throttling error</error-type></rpc-error>\n"
            + "\n#79\n"
            + "     xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "  <close-session/>\n"
            + "</rpc>"
            + "\n##\n";

    private static final String MALFORMED_MESSAGE_WITHOUT_TRAILING = "\n#4\n"
            + "<rpc"
            + "\n#18\n"
            + " message-id=\"102\"\n"
            + "<rpc-error><error-type>transport throttling error</error-type></rpc-error>\n";

    private static final String MALFORMED_MESSAGE_WITH_TRAILING_PLUS_CORRECT_MESSAGE = "\n#4\n"
            + "<rpc"
            + "\n#18\n"
            + " message-id=\"102\"\n"
            +  "<rpc-error><error-type>transport throttling error</error-type></rpc-error>\n"
            + "\n#79\n"
            + "     xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "  <close-session/>\n"
            + "</rpc>"
            + "\n##\n"
            + "\n#141\n"
            + "<rpc message-id=\"103\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "    <data>\n"
            + "        <random-value>123</random-value>\n"
            + "    </data>\n"
            + "</rpc>"
            + "\n##\n";

    private static final String EXPECTED_MESSAGE_103 = "<rpc message-id=\"103\""
            + " xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "    <data>\n"
            + "        <random-value>123</random-value>\n"
            + "    </data>\n"
            + "</rpc>";

    private static final String TWO_MALFORMED_MESSAGES = "\n#135\n"
            + "<rpc message-id=\"103\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "    <data>\n"
            + "        <random-value>123</random-value>\n"
            + "    </data>\n"
            + "    <<error>>\n"
            + "\n#6\n"
            + "</rpc>"
            + "\n##\n"
            + "\n#135\n"
            + "<rpc message-id=\"104\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "    <<error>>\n"
            + "    <data>\n"
            + "        <random-value>124</random-value>\n"
            + "    </data>\n"
            + "\n#6\n"
            + "</rpc>"
            + "\n##\n";

    private static final String MALFORMED_MESSAGE_WITHOUT_TRAILING_PLUS_CORRECT_MESSAGE = "\n#4\n"
            + "<rpc"
            + "\n#18\n"
            + " message-id=\"102\"\n"
            +  "<rpc-error><error-type>transport throttling error</error-type></rpc-error>\n"
            + "\n#141\n"
            + "<rpc message-id=\"103\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "    <data>\n"
            + "        <random-value>123</random-value>\n"
            + "    </data>\n"
            + "</rpc>"
            + "\n##\n";

    private static NetconfChunkAggregator agr;

    @Before
    public void setUp() {
        agr = new NetconfChunkAggregator();
    }

    @Test
    public void testMultipleChunks() throws Exception {
        final List<Object> output = new ArrayList<>();
        final ByteBuf input = Unpooled.copiedBuffer(CHUNKED_MESSAGE.getBytes(StandardCharsets.UTF_8));
        agr.decode(null, input, output);

        assertEquals(1, output.size());
        final ByteBuf chunk = (ByteBuf) output.get(0);

        assertEquals(EXPECTED_MESSAGE_102, chunk.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void testOneChunks() throws Exception {
        final List<Object> output = new ArrayList<>();
        final ByteBuf input = Unpooled.copiedBuffer(CHUNKED_MESSAGE_ONE.getBytes(StandardCharsets.UTF_8));
        agr.decode(null, input, output);

        assertEquals(1, output.size());
        final ByteBuf chunk = (ByteBuf) output.get(0);

        assertEquals(EXPECTED_MESSAGE_102, chunk.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void testMalformedMessageWithTrailing() {
        final ContinuousChunkAggregator chunkAggregator = new ContinuousChunkAggregator(agr);
        chunkAggregator.parseMessage(MALFORMED_MESSAGE_WITH_TRAILING);

        assertEquals(1, chunkAggregator.getCapturedExceptions().size());
        assertEquals(0, chunkAggregator.getOutput().size());
    }

    @Test
    public void testMalformedMessageWithoutTrailing() {
        final ContinuousChunkAggregator chunkAggregator = new ContinuousChunkAggregator(agr);
        chunkAggregator.parseMessage(MALFORMED_MESSAGE_WITHOUT_TRAILING);

        assertEquals(1, chunkAggregator.getCapturedExceptions().size());
        assertEquals(0, chunkAggregator.getOutput().size());
    }

    @Test
    public void testMalformedWithTrailingPlusGoodMessage() {
        final ContinuousChunkAggregator chunkAggregator = new ContinuousChunkAggregator(agr);
        chunkAggregator.parseMessage(MALFORMED_MESSAGE_WITH_TRAILING_PLUS_CORRECT_MESSAGE);

        assertEquals(1, chunkAggregator.getCapturedExceptions().size());
        assertEquals(1, chunkAggregator.getOutput().size());
        assertEquals(EXPECTED_MESSAGE_103, ((ByteBuf) chunkAggregator.getOutput().get(0))
                .toString(StandardCharsets.UTF_8));
    }

    @Test
    public void testTwoMalformedMessages() {
        final ContinuousChunkAggregator chunkAggregator = new ContinuousChunkAggregator(agr);
        chunkAggregator.parseMessage(TWO_MALFORMED_MESSAGES);

        assertEquals(2, chunkAggregator.getCapturedExceptions().size());
        assertEquals(0, chunkAggregator.getOutput().size());
    }

    @Test
    public void testMalformedWithoutTrailingPlusGoodMessage() {
        final ContinuousChunkAggregator chunkAggregator = new ContinuousChunkAggregator(agr);
        chunkAggregator.parseMessage(MALFORMED_MESSAGE_WITHOUT_TRAILING_PLUS_CORRECT_MESSAGE);

        assertEquals(1, chunkAggregator.getCapturedExceptions().size());
        assertEquals(1, chunkAggregator.getOutput().size());
        assertEquals(EXPECTED_MESSAGE_103, ((ByteBuf) chunkAggregator.getOutput().get(0))
                .toString(StandardCharsets.UTF_8));
    }

    /**
     * Wrapper for {@link NetconfChunkAggregator} that simulates behaviour of the Netty framework:
     * {@link NetconfChunkAggregator#decode(ChannelHandlerContext, ByteBuf, List)} is called till either the input
     * {@link ByteBuf} has nothing to read when return from this method or till nothing was read from the input
     * {@link ByteBuf}.
     */
    private static final class ContinuousChunkAggregator {
        private final List<Object> output = new ArrayList<>();
        private final List<NetconfChunkException> capturedExceptions = new ArrayList<>();
        private final NetconfChunkAggregator chunkAggregator;

        ContinuousChunkAggregator(final NetconfChunkAggregator chunkAggregator) {
            this.chunkAggregator = chunkAggregator;
        }

        void parseMessage(final String message) {
            final ByteBuf input = Unpooled.copiedBuffer(message.getBytes(StandardCharsets.UTF_8));
            parseByteBuffer(input);
        }

        private void parseByteBuffer(final ByteBuf input) {
            final int readableBytesBeforeParsing = input.readableBytes();
            try {
                chunkAggregator.decode(null, input, output);
            } catch (NetconfChunkException e) {
                capturedExceptions.add(e);
            }
            final int readableBytesAfterParsing = input.readableBytes();
            if (readableBytesAfterParsing != readableBytesBeforeParsing && readableBytesAfterParsing != 0) {
                parseByteBuffer(input);
            }
        }

        List<Object> getOutput() {
            return Collections.unmodifiableList(output);
        }

        List<NetconfChunkException> getCapturedExceptions() {
            return Collections.unmodifiableList(capturedExceptions);
        }
    }
}