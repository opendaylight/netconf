/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static com.google.common.base.Preconditions.checkArgument;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import org.checkerframework.checker.index.qual.NonNegative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfChunkAggregator extends ByteToMessageDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfChunkAggregator.class);
    private static final String GOT_PARAM_WHILE_WAITING_FOR_PARAM = "Got byte {} while waiting for {}";
    private static final String GOT_PARAM_WHILE_WAITING_FOR_PARAM_PARAM = "Got byte {} while waiting for {}-{}";
    private static final String GOT_PARAM_WHILE_WAITING_FOR_PARAM_PARAM_PARAM =
        "Got byte {} while waiting for {}-{}-{}";

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
    }

    private final int maxChunkSize;
    private State state = State.HEADER_ONE;
    private long chunkSize;
    private CompositeByteBuf chunk;

    /**
     * Construct an instance with specified maximum chunk size.
     *
     * @param maxChunkSize maximum chunk size
     * @throws IllegalArgumentException if {@code maxChunkSize} is negative
     */
    public NetconfChunkAggregator(final @NonNegative int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        checkArgument(maxChunkSize > 0, "Negative maximum chunk size %s", maxChunkSize);
    }

    private static void checkNewLine(final byte byteToCheck, final String errorMessage) {
        if (byteToCheck != '\n') {
            LOG.debug(GOT_PARAM_WHILE_WAITING_FOR_PARAM, byteToCheck, (byte)'\n');
            throw new IllegalStateException(errorMessage);
        }
    }

    private static void checkHash(final byte byteToCheck, final String errorMessage) {
        if (byteToCheck != '#') {
            LOG.debug(GOT_PARAM_WHILE_WAITING_FOR_PARAM, byteToCheck, (byte)'#');
            throw new IllegalStateException(errorMessage);
        }
    }

    private void checkChunkSize() {
        if (chunkSize > maxChunkSize) {
            LOG.debug("Parsed chunk size {}, maximum allowed is {}", chunkSize, maxChunkSize);
            throw new IllegalStateException("Chunk size " + chunkSize + " exceeds maximum " + maxChunkSize);
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in, final List<Object> out) throws IllegalStateException {
        while (in.isReadable()) {
            switch (state) {
                case HEADER_ONE: {
                    final byte b = in.readByte();
                    checkNewLine(b, "Malformed chunk header encountered (byte 0)");
                    state = State.HEADER_TWO;
                    initChunk();
                    break;
                }
                case HEADER_TWO: {
                    final byte b = in.readByte();
                    checkHash(b, "Malformed chunk header encountered (byte 1)");
                    state = State.HEADER_LENGTH_FIRST;
                    break;
                }
                case HEADER_LENGTH_FIRST: {
                    final byte b = in.readByte();
                    chunkSize = processHeaderLengthFirst(b);
                    state = State.HEADER_LENGTH_OTHER;
                    break;
                }
                case HEADER_LENGTH_OTHER: {
                    final byte b = in.readByte();
                    if (b == '\n') {
                        state = State.DATA;
                        break;
                    }
                    if (b < '0' || b > '9') {
                        LOG.debug(GOT_PARAM_WHILE_WAITING_FOR_PARAM_PARAM, b, (byte)'0', (byte)'9');
                        throw new IllegalStateException("Invalid chunk size encountered");
                    }
                    chunkSize *= 10;
                    chunkSize += b - '0';
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
                        return;
                    }
                    aggregateChunks(in.readBytes((int) chunkSize));
                    state = State.FOOTER_ONE;
                    break;
                case FOOTER_ONE: {
                    final byte b = in.readByte();
                    checkNewLine(b,"Malformed chunk footer encountered (byte 0)");
                    state = State.FOOTER_TWO;
                    chunkSize = 0;
                    break;
                }
                case FOOTER_TWO: {
                    final byte b = in.readByte();
                    checkHash(b,"Malformed chunk footer encountered (byte 1)");
                    state = State.FOOTER_THREE;
                    break;
                }
                case FOOTER_THREE: {
                    final byte b = in.readByte();
                    // In this state, either header-of-new-chunk or message-end is expected
                    // Depends on the next character
                    extractNewChunkOrMessageEnd(b);
                    break;
                }
                case FOOTER_FOUR: {
                    final byte b = in.readByte();
                    checkNewLine(b,"Malformed chunk footer encountered (byte 3)");
                    state = State.HEADER_ONE;
                    out.add(chunk);
                    chunk = null;
                    break;
                }
                default:
                    LOG.info("Unknown state.");
            }
        }

        in.discardReadBytes();
    }

    private void extractNewChunkOrMessageEnd(final byte byteToCheck) {
        if (isHeaderLengthFirst(byteToCheck)) {
            // Extract header length#1 from new chunk
            chunkSize = processHeaderLengthFirst(byteToCheck);
            // Proceed with next chunk processing
            state = State.HEADER_LENGTH_OTHER;
        } else if (byteToCheck == '#') {
            state = State.FOOTER_FOUR;
        } else {
            LOG.debug(GOT_PARAM_WHILE_WAITING_FOR_PARAM_PARAM_PARAM, byteToCheck, (byte) '#', (byte) '1', (byte) '9');
            throw new IllegalStateException("Malformed chunk footer encountered (byte 2)");
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

    private static int processHeaderLengthFirst(final byte byteToCheck) {
        if (!isHeaderLengthFirst(byteToCheck)) {
            LOG.debug(GOT_PARAM_WHILE_WAITING_FOR_PARAM_PARAM, byteToCheck, (byte)'1', (byte)'9');
            throw new IllegalStateException("Invalid chunk size encountered (byte 0)");
        }

        return byteToCheck - '0';
    }

    private static boolean isHeaderLengthFirst(final byte byteToCheck) {
        return byteToCheck >= '1' && byteToCheck <= '9';
    }
}
