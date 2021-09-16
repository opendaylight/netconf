/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.odlparent.logging.markers.Markers;
import org.opendaylight.yangtools.concepts.Mutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A buffer of bytes in lazily-allocated array, which can be appended with {@link #appendByte(int)}. Current contents
 * can be transferred to a {@link StringBuilder} via {@link #flushTo(StringBuilder, int)}, which performs UTF-8
 * character decoding.
 */
final class Utf8Buffer implements Mutable {
    private static final Logger LOG = LoggerFactory.getLogger(Utf8Buffer.class);

    private ByteArrayOutputStream bos;
    private CharsetDecoder decoder;

    void appendByte(final byte value) {
        var buf = bos;
        if (buf == null) {
            bos = buf = new ByteArrayOutputStream(8);
        }
        buf.write(value);
    }

    void flushTo(final @NonNull StringBuilder sb, final int errorOffset) throws ParseException {
        final var buf = bos;
        if (buf != null && buf.size() != 0) {
            flushTo(sb, buf, errorOffset);
        }
    }

    // Split out to aid inlining
    private void flushTo(final StringBuilder sb, final ByteArrayOutputStream buf, final int errorOffset)
            throws ParseException {
        final var bytes = buf.toByteArray();
        buf.reset();

        // Special case for a single ASCII character, side-steps decoder/bytebuf allocation
        if (bytes.length == 1) {
            final byte ch = bytes[0];
            if (ch >= 0) {
                sb.append((char) ch);
                return;
            }
        }
        try {
            append(sb, ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            throw report(errorOffset, bytes, e);
        }
    }

    private void append(final StringBuilder sb, final ByteBuffer bytes) throws CharacterCodingException {
        var local = decoder;
        if (local == null) {
            decoder = local = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
        sb.append(local.decode(bytes));
    }

    // Split out to silence checkstyle's failure to understand we cannot propagate the cause
    private static ParseException report(final int errorOffset, final byte[] bytes,
            final CharacterCodingException cause) {
        final String str = new String(bytes, StandardCharsets.UTF_8);
        LOG.debug(Markers.confidential(), "Rejecting invalid UTF-8 sequence '{}'", str, cause);
        return new ParseException("Invalid UTF-8 sequence '" + str + "': " + cause.getMessage(), errorOffset);
    }
}