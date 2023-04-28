/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.opendaylight.netconf.api.messages.FramingMechanism;

/**
 * netconf message part constants as bytes.
 *
 * @author Thomas Pantelis
 */
final class MessageParts {
    static final byte[] END_OF_MESSAGE;
    static final byte[] START_OF_CHUNK;
    static final byte[] END_OF_CHUNK;

    static {
        final var encoder = StandardCharsets.US_ASCII.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            END_OF_MESSAGE = getBytes(encoder, FramingMechanism.EOM_STR);
            START_OF_CHUNK = getBytes(encoder, FramingMechanism.CHUNK_START_STR);
            END_OF_CHUNK = getBytes(encoder, FramingMechanism.CHUNK_END_STR);
        } catch (CharacterCodingException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private MessageParts() {
        // Hidden on purpose
    }

    private static byte[] getBytes(final CharsetEncoder encoder, final String str) throws CharacterCodingException {
        final var buf = encoder.encode(CharBuffer.wrap(str));
        final var bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return bytes;
    }
}
