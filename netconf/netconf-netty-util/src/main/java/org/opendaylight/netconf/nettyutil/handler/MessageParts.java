/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.opendaylight.netconf.util.messages.FramingMechanism;

/**
 * netconf message part constants as bytes.
 *
 * @author Thomas Pantelis
 */
final class MessageParts {
    static final byte[] END_OF_MESSAGE = asciiBytes(FramingMechanism.EOM_STR);
    static final byte[] START_OF_CHUNK = asciiBytes(FramingMechanism.CHUNK_START_STR);
    static final byte[] END_OF_CHUNK = asciiBytes(FramingMechanism.CHUNK_END_STR);

    private MessageParts() {
        // Hidden on purpose
    }

    private static byte[] asciiBytes(final String str) {
        final ByteBuffer buf;
        try {
            buf = StandardCharsets.US_ASCII.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(str));
        } catch (CharacterCodingException e) {
            throw new ExceptionInInitializerError(e);
        }

        final byte[] ret = new byte[buf.remaining()];
        buf.get(ret);
        return ret;
    }
}
