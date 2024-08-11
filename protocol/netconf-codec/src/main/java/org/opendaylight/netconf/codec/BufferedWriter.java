/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import java.io.IOException;
import java.io.Writer;

/**
 * Custom BufferedWriter optimized for NETCONF pipeline implemented instead of default BufferedWriter provided by JDK..
 *
 * <p>
 * In versions up to and including Java 8, java.io.BufferedWriter initialized its lineSeparator field using
 * AccessController and takes considerable amount of time especially if lots of BufferedWriters are created in the
 * system. This has been rectified in <a href="https://bugs.openjdk.org/browse/JDK-8068498">Java 9</a>.
 *
 * <p>
 * Despite that issue having been fixed we retain this implementation because its methods are not synchronized, hence
 * offer a tad better performance.
 *
 * <p>
 * This implementation should only be used if newLine method is not required such as NETCONF message to XML encoders.
 */
public final class BufferedWriter extends Writer {
    private static final int DEFAULT_CHAR_BUFFER_SIZE = 8192;

    private final Writer writer;
    private final char[] buffer;
    private final int bufferSize;

    private int nextChar = 0;

    public BufferedWriter(final Writer writer) {
        this(writer, DEFAULT_CHAR_BUFFER_SIZE);
    }

    public BufferedWriter(final Writer writer, final int bufferSize) {
        super(writer);
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        this.writer = writer;
        this.bufferSize = bufferSize;
        buffer = new char[bufferSize];
    }

    private void flushBuffer() throws IOException {
        if (nextChar == 0) {
            return;
        }
        writer.write(buffer, 0, nextChar);
        nextChar = 0;
    }

    @Override
    public void write(final int character) throws IOException {
        if (nextChar >= bufferSize) {
            flushBuffer();
        }
        buffer[nextChar++] = (char) character;
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public void write(final char[] buffer, final int offset, final int length) throws IOException {
        if (offset < 0 || offset > buffer.length
                || length < 0 || offset + length > buffer.length || offset + length < 0) {
            throw new IndexOutOfBoundsException(
                "Buffer size: %d, Offset: %d, Length: %d".formatted(buffer.length, offset, length));
        } else if (length == 0) {
            return;
        }

        if (length >= bufferSize) {
            flushBuffer();
            writer.write(buffer, offset, length);
            return;
        }

        int bufferOffset = offset;
        final int t = offset + length;
        while (bufferOffset < t) {
            final int d = Math.min(bufferSize - nextChar, t - bufferOffset);
            System.arraycopy(buffer, bufferOffset, this.buffer, nextChar, d);
            bufferOffset += d;
            nextChar += d;
            if (nextChar >= bufferSize) {
                flushBuffer();
            }
        }
    }

    @Override
    public void write(final String string, final int offset, final int length) throws IOException {
        int bufferOffset = offset;
        final int t = offset + length;
        while (bufferOffset < t) {
            final int d = Math.min(bufferSize - nextChar, t - bufferOffset);
            string.getChars(bufferOffset, bufferOffset + d, buffer, nextChar);
            bufferOffset += d;
            nextChar += d;
            if (nextChar >= bufferSize) {
                flushBuffer();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        flushBuffer();
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        try {
            flushBuffer();
        } finally {
            writer.close();
        }
    }
}
