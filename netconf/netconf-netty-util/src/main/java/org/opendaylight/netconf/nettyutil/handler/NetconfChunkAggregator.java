/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendaylight.netconf.api.NetconfChunkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfChunkAggregator extends ByteToMessageDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfChunkAggregator.class);
    private static final String GOT_PARAM_WHILE_WAITING_FOR_PARAM = "Got byte {} while waiting for {}";
    private static final String GOT_PARAM_WHILE_WAITING_FOR_RANGE = "Got byte {} while waiting for range {}";
    private static final String GOT_PARAM_WHILE_WAITING_FOR_RANGE_OR_PARAM
            = "Got byte {} while waiting for range {} or {}";

    private static final int DEFAULT_MAXIMUM_CHUNK_SIZE = 16 * 1024 * 1024;
    private static final byte NEW_LINE_BYTE = (byte) '\n';
    private static final byte HASH_BYTE = (byte) '#';
    private static final char NUM_0_CHAR = '0';
    private static final char NUM_1_CHAR = '1';
    private static final char NUM_9_CHAR = '9';
    private static final ByteRange NUM_1_9_RANGE = ByteRange.createRange(NUM_1_CHAR, NUM_9_CHAR);
    private static final ByteRange NUM_0_9_RANGE = ByteRange.createRange(NUM_0_CHAR, NUM_9_CHAR);

    private static final Pattern RPC_HEADER_PATTERN = Pattern.compile(
            "^<rpc.*?message-id=\".+?\"[^<]*>.*?", Pattern.MULTILINE | Pattern.DOTALL);

    private enum State {
        HEADER_ONE, // \n
        HEADER_TWO, // #
        HEADER_LENGTH_FIRST, // [1-9]
        HEADER_LENGTH_OTHER, // [0-9]*\n
        DATA,
        FOOTER_ONE, // \n
        FOOTER_TWO, // #
        FOOTER_THREE, // #
        FOOTER_FOUR, // \n
        /**
         * The state of chunk aggregator can move to this state from all states except the {@link #DATA} state, if:<br>
         * 1. the value of the following byte doesn't conform RFC 6242 (the footer or header of chunk
         *    is not correctly encoded),<br>
         * 2. parsed chunk size exceeds {@value DEFAULT_MAXIMUM_CHUNK_SIZE} bytes.<br>
         * <br>
         * If chunk aggregator is already in this state, there are two cases that are considered:<br>
         * 1. Received byte that doesn't equal to {@value NEW_LINE_BYTE} - it will be ignored and the state of chunk
         *    aggregator will no be changed.<br>
         * 2. Received {@value NEW_LINE_BYTE} byte (newline character) - it causes transition to {@link #HEADER_ONE}
         *    state because there is a chance that the following chunk will be properly encoded.<br>
         * <br>
         * Afterwards there are two scenarios according to which the state of chunk aggregator may evolve beginning
         * by received newline byte:<br>
         * 1. If the next bytes don't represent a valid chunk, then the state will naturally return
         *    to MALFORMED_DATA state.<br>
         * 2. If the next bytes represents a valid chunk but this chunk doesn't contain a NETCONF RPC header,
         *    then this chunk will not be added to the output list of parsed chunks.<br>
         * 3. If the next bytes represents a valid chunk and this chunk contains a NETCONF RPC header, then this
         *    chunk will be added to the output list parsed chunks.
         */
        MALFORMED_DATA // \n
    }

    private State state = State.HEADER_ONE;
    private long chunkSize;
    private CompositeByteBuf chunk;
    /**
     * If this flag is set to {@code true}, exceptions are propagated to next handler in NETCONF channel pipeline.
     * Otherwise, occurred exceptions and related warning messages are suppressed. Description of the states:<br>
     * - Initially, propagation of the exception is enabled (value is set to {@code true}).<br>
     * - After an {@link NetconfChunkException} occurs while parsing NETCONF chunks, propagation of exceptions
     *   is disabled (value is set to {@code false}) and the exception is rethrown.<br>
     * - While the value of this flag stays on {@code false}, next {@link NetconfChunkException} are ignored.
     *   The intention of such behaviour is decreasing the rate of errors that otherwise would have to be processed
     *   in next Netty handlers. However, in the most cases the first chunk error also causes next chunk errors since
     *   chunk size is not correctly parsed.<br>
     * - After a new complete chunk that contains RPC header, matched by {@link #RPC_HEADER_PATTERN}, is found
     *   propagation of exceptions is re-enabled by setting of value to {@code true}.
     */
    private boolean propagationEnabled = true;

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in,
                          final List<Object> out) throws NetconfChunkException {
        while (in.isReadable()) {
            try {
                if (decodeNextByte(in, out)) {
                    return;
                }
            } catch (NetconfChunkException e) {
                if (propagationEnabled) {
                    // propagation of parsed chunks and exceptions will be disabled till the beginning of new message
                    propagationEnabled = false;
                    throw e;
                }
            }
        }

        in.discardReadBytes();
    }

    /**
     * Decoding of the next byte from the input buffer.
     *
     * @param in  Input byte buffer.
     * @param out Output parsed chunks.
     * @return Whether to stop decoding of next bytes {@code true} or not - {@code false}.
     * @throws NetconfChunkException Unexpected state - chunk cannot be parsed.
     */
    private boolean decodeNextByte(final ByteBuf in, final List<Object> out) throws NetconfChunkException {
        switch (state) {
            case HEADER_ONE: {
                verifyNextByte(in.readByte(), NEW_LINE_BYTE, "Malformed chunk header encountered (byte 0)");
                state = State.HEADER_TWO;
                initChunk();
                break;
            }
            case HEADER_TWO: {
                verifyNextByte(in.readByte(), HASH_BYTE, "Malformed chunk header encountered (byte 1)");
                state = State.HEADER_LENGTH_FIRST;
                break;
            }
            case HEADER_LENGTH_FIRST: {
                final byte nextByte = in.readByte();
                verifyNextByteIsInRange(nextByte, NUM_1_9_RANGE);
                chunkSize = nextByte - NUM_0_CHAR;
                state = State.HEADER_LENGTH_OTHER;
                break;
            }
            case HEADER_LENGTH_OTHER: {
                final byte nextByte = in.readByte();
                if (nextByte == NEW_LINE_BYTE) {
                    state = State.DATA;
                    break;
                }
                verifyNextByteIsInRange(nextByte, NUM_0_9_RANGE);

                chunkSize *= 10;
                chunkSize += nextByte - NUM_0_CHAR;
                checkChunkSize();
                break;
            }
            case DATA:
                /*
                 * FIXME: this gathers all data into one big chunk before passing
                 *        it on. Make sure the pipeline can work with partial data
                 *        and then change this piece to pass the data on as it
                 *        comes through.
                 */
                if (in.readableBytes() < chunkSize) {
                    LOG.debug("Buffer has {} bytes, need {} to complete chunk", in.readableBytes(), chunkSize);
                    in.discardReadBytes();
                    return true;
                }
                aggregateChunks(in.readBytes((int) chunkSize));
                state = State.FOOTER_ONE;
                break;
            case FOOTER_ONE: {
                verifyNextByte(in.readByte(), NEW_LINE_BYTE, "Malformed chunk footer encountered (byte 0)");
                state = State.FOOTER_TWO;
                chunkSize = 0;
                break;
            }
            case FOOTER_TWO: {
                verifyNextByte(in.readByte(), HASH_BYTE, "Malformed chunk footer encountered (byte 1)");
                state = State.FOOTER_THREE;
                break;
            }
            case FOOTER_THREE: {
                final byte nextByte = in.readByte();
                // In this state, either header-of-new-chunk or message-end is expected
                // Depends on the next character
                extractNewChunkOrMessageEnd(nextByte);
                break;
            }
            case FOOTER_FOUR: {
                verifyNextByte(in.readByte(), NEW_LINE_BYTE, "Malformed chunk footer encountered (byte 3)");
                state = State.HEADER_ONE;
                if (propagationEnabled || chunkStartsWithRpcHeader()) {
                    out.add(chunk);
                    chunk = null;
                }
                propagationEnabled = true;
                break;
            }
            case MALFORMED_DATA: {
                final byte nextData = in.readByte();
                if (nextData == NEW_LINE_BYTE) {
                    state = State.HEADER_ONE;
                }
                chunk = null;
                break;
            }
            default:
                LOG.error("Unknown state.");
        }
        return false;
    }

    private boolean chunkStartsWithRpcHeader() {
        final String bufferContents = chunk.duplicate().toString(StandardCharsets.UTF_8);
        final Matcher rpcHeaderMatcher = RPC_HEADER_PATTERN.matcher(bufferContents);
        return rpcHeaderMatcher.matches();
    }

    private void extractNewChunkOrMessageEnd(final byte nextByte) throws NetconfChunkException {
        if (NUM_1_9_RANGE.isInRange(nextByte)) {
            // Extract header length#1 from new chunk
            chunkSize = nextByte - NUM_0_CHAR;
            // Proceed with next chunk processing
            state = State.HEADER_LENGTH_OTHER;
        } else if (nextByte == HASH_BYTE) {
            state = State.FOOTER_FOUR;
        } else {
            final byte[] cachedBytes = getCachedBytes();
            printWarning(GOT_PARAM_WHILE_WAITING_FOR_RANGE_OR_PARAM, nextByte, NUM_1_9_RANGE, HASH_BYTE);
            state = State.MALFORMED_DATA;
            throw NetconfChunkException.create(cachedBytes, "Malformed chunk footer encountered (byte 2)");
        }
    }

    private void initChunk() {
        chunk = Unpooled.compositeBuffer();
    }

    private void aggregateChunks(final ByteBuf newChunk) {
        chunk.addComponent(chunk.numComponents(), newChunk);

        // Update writer index, addComponent does not update it
        chunk.writerIndex(chunk.writerIndex() + newChunk.readableBytes());
    }

    private void verifyNextByte(final byte nextByte, final byte expectedByte,
                                final String errorMessage) throws NetconfChunkException {
        if (nextByte != expectedByte) {
            final byte[] cachedBytes = getCachedBytes();
            printWarning(GOT_PARAM_WHILE_WAITING_FOR_PARAM, nextByte, expectedByte);
            state = State.MALFORMED_DATA;
            throw NetconfChunkException.create(cachedBytes, errorMessage);
        }
    }

    private void verifyNextByteIsInRange(final byte nextByte,
                                         final ByteRange expectedRange) throws NetconfChunkException {
        if (!expectedRange.isInRange(nextByte)) {
            final byte[] cachedBytes = getCachedBytes();
            printWarning(GOT_PARAM_WHILE_WAITING_FOR_RANGE, nextByte, expectedRange);
            state = State.MALFORMED_DATA;
            throw NetconfChunkException.create(cachedBytes, "Invalid chunk size encountered (byte 0)");
        }
    }

    private void checkChunkSize() throws NetconfChunkException {
        if (chunkSize > DEFAULT_MAXIMUM_CHUNK_SIZE) {
            final byte[] cachedBytes = getCachedBytes();
            printWarning("Parsed chunk size {}, maximum allowed is {}; cached chunk bytes:\n{}",
                    chunkSize, DEFAULT_MAXIMUM_CHUNK_SIZE, cachedBytes);
            state = State.MALFORMED_DATA;
            throw NetconfChunkException.create(cachedBytes, "Maximum chunk size exceeded");
        }
    }

    @SuppressFBWarnings("SLF4J_UNKNOWN_ARRAY")
    private void printWarning(final String message, final Object... args) {
        if (propagationEnabled) {
            LOG.warn(message, args);
        }
    }

    private byte[] getCachedBytes() {
        final byte[] cachedBytes;
        if (chunk != null && chunk.isReadable()) {
            cachedBytes = new byte[chunk.readableBytes()];
            chunk.getBytes(chunk.readerIndex(), cachedBytes);
        } else {
            cachedBytes = new byte[0];
        }
        return cachedBytes;
    }

    private static final class ByteRange {
        private final byte lowerBoundIncl;
        private final byte upperBoundIncl;

        private ByteRange(final byte lowerBoundIncl, final byte upperBoundIncl) {
            this.lowerBoundIncl = lowerBoundIncl;
            this.upperBoundIncl = upperBoundIncl;
        }

        static ByteRange createRange(final char firstChar, final char lastChar) {
            return new ByteRange((byte) firstChar, (byte) lastChar);
        }

        boolean isInRange(final byte character) {
            return character >= lowerBoundIncl && character <= upperBoundIncl;
        }

        @Override
        public String toString() {
            return lowerBoundIncl + "-" + upperBoundIncl;
        }
    }
}