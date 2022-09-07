/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

final class ByteBufStr {
    private static final Pattern NON_ASCII = Pattern.compile("([^\\x20-\\x7E\\x0D\\x0A])+");

    private final ByteBuf buf;

    ByteBufStr(final ByteBuf buf) {
        this.buf = requireNonNull(buf);
    }

    @Override
    public String toString() {
        final String message = buf.toString(StandardCharsets.UTF_8);
        buf.resetReaderIndex();
        return NON_ASCII.matcher(message).replaceAll(data -> {
            final var buf = new StringBuilder().append('"');
            for (byte b : data.group().getBytes(StandardCharsets.US_ASCII)) {
                buf.append(String.format("%02X", b));
            }
            return buf.append('"').toString();
        });
    }
}
